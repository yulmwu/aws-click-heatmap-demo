package com.demo.heatmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.kinesis.source.config.KinesisStreamsSourceConfigConstants;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class HeatmapJob {

    private static final int GRID_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String APP_PROPERTIES_PATH = "/etc/flink/application_properties.json";

    private static Map<String, String> loadAppProperties() {
        try {
            if (!Files.exists(Paths.get(APP_PROPERTIES_PATH))) {
                return Map.of();
            }
            JsonNode root = MAPPER.readTree(Files.newBufferedReader(Paths.get(APP_PROPERTIES_PATH)));
            Map<String, String> out = new HashMap<>();
            if (root.isObject()) {
                JsonNode group = root.get("FlinkApplicationProperties");
                if (group != null && group.isObject()) {
                    group.fields().forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue().asText()));
                    return out;
                }
                JsonNode groups = root.get("PropertyGroups");
                if (groups == null) {
                    groups = root.get("propertyGroups");
                }
                if (groups != null && groups.isArray()) {
                    readGroupsArray(groups, out);
                    return out;
                }
                if (isGroupNode(root)) {
                    readGroup(root, out);
                    return out;
                }
            } else if (root.isArray()) {
                readGroupsArray(root, out);
                return out;
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static void readGroupsArray(JsonNode groups, Map<String, String> out) {
        for (JsonNode item : groups) {
            String groupId = getGroupId(item);
            if ("FlinkApplicationProperties".equals(groupId)) {
                readGroup(item, out);
                return;
            }
        }
    }

    private static void readGroup(JsonNode group, Map<String, String> out) {
        JsonNode propertyMap = group.get("propertyMap");
        if (propertyMap == null) {
            propertyMap = group.get("PropertyMap");
        }
        if (propertyMap == null) {
            propertyMap = group.get("property_map");
        }
        if (propertyMap != null && propertyMap.isObject()) {
            propertyMap.fields().forEachRemaining(entry -> out.put(entry.getKey(), entry.getValue().asText()));
        }
    }

    private static boolean isGroupNode(JsonNode node) {
        return getGroupId(node) != null && (node.has("propertyMap") || node.has("PropertyMap") || node.has("property_map"));
    }

    private static String getGroupId(JsonNode node) {
        JsonNode groupId = node.get("propertyGroupId");
        if (groupId == null) {
            groupId = node.get("PropertyGroupId");
        }
        if (groupId == null) {
            groupId = node.get("property_group_id");
        }
        if (groupId != null && groupId.isTextual()) {
            return groupId.asText();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        ParameterTool parameters = ParameterTool.fromSystemProperties()
            .mergeWith(ParameterTool.fromMap(System.getenv()))
            .mergeWith(ParameterTool.fromMap(loadAppProperties()))
            .mergeWith(ParameterTool.fromArgs(args));
        env.getConfig().setGlobalJobParameters(parameters);

        String streamArn = parameters.getRequired("KINESIS_STREAM_ARN");
        String awsRegion = parameters.get("AWS_REGION", "ap-northeast-2");
        System.setProperty("aws.region", awsRegion);
        Configuration sourceConfig = new Configuration();
        sourceConfig.set(KinesisStreamsSourceConfigConstants.STREAM_INITIAL_POSITION,
            KinesisStreamsSourceConfigConstants.InitialPosition.LATEST);
        sourceConfig.set(KinesisStreamsSourceConfigConstants.SHARD_DISCOVERY_INTERVAL_MILLIS, 10000L);

        KinesisStreamsSource<String> source =
            KinesisStreamsSource.<String>builder()
                .setStreamArn(streamArn)
                .setSourceConfig(sourceConfig)
                .setDeserializationSchema(new SimpleStringSchema())
                .build();

        DataStream<HeatmapAggregate> aggregated =
            env.fromSource(source, WatermarkStrategy.noWatermarks(), "kinesis-source")
                .flatMap((String json, org.apache.flink.util.Collector<HeatmapAggregate> out) -> {
                    Optional<HeatmapAggregate> agg = parseEvent(json);
                    agg.ifPresent(out::collect);
                })
                .returns(HeatmapAggregate.class)
                .keyBy(agg -> agg.pageId + "-" + agg.gridX + "-" + agg.gridY)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
                .reduce((agg1, agg2) -> {
                    agg1.clicks += agg2.clicks;
                    agg1.windowStart = Math.min(agg1.windowStart, agg2.windowStart);
                    agg1.windowEnd = Math.max(agg1.windowEnd, agg2.windowEnd);
                    return agg1;
                });

        String outputPath = parameters.getRequired("CURATED_S3_PATH");
        if (!outputPath.endsWith("/")) outputPath += "/";
        if (!outputPath.endsWith("curated_heatmap/")) {
            outputPath += "curated_heatmap/";
        }
        
        Schema avroSchema = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"HeatmapAggregate\",\"namespace\":\"com.demo.heatmap\"," +
            "\"fields\":[" +
            "{\"name\":\"page_id\",\"type\":\"string\"}," +
            "{\"name\":\"grid_x\",\"type\":\"int\"}," +
            "{\"name\":\"grid_y\",\"type\":\"int\"}," +
            "{\"name\":\"window_start\",\"type\":\"long\"}," +
            "{\"name\":\"window_end\",\"type\":\"long\"}," +
            "{\"name\":\"clicks\",\"type\":\"long\"}" +
            "]}"
        );

        FileSink<GenericRecord> sink = FileSink
            .forBulkFormat(new Path(outputPath), AvroParquetWriters.forGenericRecord(avroSchema))
            .withRollingPolicy(OnCheckpointRollingPolicy.build())
            .build();

        aggregated.map(agg -> {
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("page_id", agg.pageId);
            record.put("grid_x", agg.gridX);
            record.put("grid_y", agg.gridY);
            record.put("window_start", agg.windowStart);
            record.put("window_end", agg.windowEnd);
            record.put("clicks", agg.clicks);
            return record;
        }).sinkTo(sink);

        env.execute("heatmap-flink-job");
    }

    private static Optional<HeatmapAggregate> parseEvent(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            HeatmapAggregate agg = new HeatmapAggregate();
            agg.pageId = node.has("page_id") ? node.get("page_id").asText() : "default";

            int x = node.has("x") ? node.get("x").asInt() : 0;
            int y = node.has("y") ? node.get("y").asInt() : 0;
            int vw = node.has("viewport_width") ? node.get("viewport_width").asInt() : 1920;
            int vh = node.has("viewport_height") ? node.get("viewport_height").asInt() : 1080;
            long ts = node.has("event_time_ms") ? node.get("event_time_ms").asLong() : System.currentTimeMillis();

            agg.gridX = Math.min(GRID_SIZE - 1, (x * GRID_SIZE) / Math.max(1, vw));
            agg.gridY = Math.min(GRID_SIZE - 1, (y * GRID_SIZE) / Math.max(1, vh));
            agg.clicks = 1;
            agg.windowStart = ts;
            agg.windowEnd = ts;
            return Optional.of(agg);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
