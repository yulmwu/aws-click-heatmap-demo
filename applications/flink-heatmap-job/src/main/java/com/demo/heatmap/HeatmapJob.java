package com.demo.heatmap;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.kinesis.source.config.KinesisStreamsSourceConfigConstants;
import org.apache.flink.connector.aws.config.AWSConfigConstants;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeatmapJob {

    private static final int GRID_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";
    private static final Logger LOG = LoggerFactory.getLogger(HeatmapJob.class);

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    private static java.util.Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            java.net.URL resource = HeatmapJob.class.getClassLoader().getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            Preconditions.checkNotNull(resource, "Local application properties resource not found");
            return KinesisAnalyticsRuntime.getApplicationProperties(
                resource.getPath());
        }
        return KinesisAnalyticsRuntime.getApplicationProperties();
    }

    private static Properties selectApplicationProperties(java.util.Map<String, Properties> applicationProperties) {
        if (applicationProperties == null || applicationProperties.isEmpty()) {
            throw new IllegalArgumentException("Application properties not found");
        }
        Properties props = applicationProperties.get(APPLICATION_CONFIG_GROUP);
        if (props != null) {
            return props;
        }
        if (applicationProperties.size() == 1) {
            return applicationProperties.values().iterator().next();
        }
        throw new IllegalArgumentException("Application properties group not found: " + APPLICATION_CONFIG_GROUP);
    }

    private static String getRequiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) value = properties.getProperty(key.toUpperCase());
        if (value == null) value = properties.getProperty(key.toLowerCase());
        if (value == null) value = properties.getProperty(key.replace('_', '.'));
        if (value == null) value = properties.getProperty(key.replace('_', '.').toLowerCase());
        if (value == null) {
            Set<String> keys = properties.stringPropertyNames();
            throw new IllegalArgumentException(key + " is required. Available keys=" + keys);
        }
        return value;
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        java.util.Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties groups: {}", applicationProperties.keySet());
        Properties props = selectApplicationProperties(applicationProperties);

        String streamArn = getRequiredProperty(props, "KINESIS_STREAM_ARN");
        String awsRegion = props.getProperty("AWS_REGION", "ap-northeast-2");
        System.setProperty("aws.region", awsRegion);
        Configuration sourceConfig = new Configuration();
        sourceConfig.setString(AWSConfigConstants.AWS_REGION, awsRegion);
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
            env.fromSource(source, WatermarkStrategy.noWatermarks(), "kinesis-source", Types.STRING)
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

        String outputPath = getRequiredProperty(props, "CURATED_S3_PATH");
        if (!outputPath.endsWith("/")) outputPath += "/";
        if (!outputPath.endsWith("curated_heatmap/")) {
            outputPath += "curated_heatmap/";
        }
        
        FileSink<HeatmapAggregate> sink = FileSink
            .forBulkFormat(new Path(outputPath), AvroParquetWriters.forReflectRecord(HeatmapAggregate.class))
            .withBucketAssigner(new DateTimeBucketAssigner<>("'partition_0='yyyy-MM-dd-HH", ZoneId.of("UTC")))
            .withRollingPolicy(OnCheckpointRollingPolicy.build())
            .build();

        aggregated.sinkTo(sink);

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
