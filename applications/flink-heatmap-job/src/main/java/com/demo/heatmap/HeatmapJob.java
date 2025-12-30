package com.demo.heatmap;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.aws.config.AWSConfigConstants;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.kinesis.source.config.KinesisStreamsSourceConfigConstants;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.Preconditions;

public class HeatmapJob {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int GRID_SIZE = 20;
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            URL resource = HeatmapJob.class.getClassLoader().getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            Preconditions.checkNotNull(resource, "Local application properties resource not found");
            return KinesisAnalyticsRuntime.getApplicationProperties(resource.getPath());
        }
        return KinesisAnalyticsRuntime.getApplicationProperties();
    }

    private static Properties selectApplicationProperties(Map<String, Properties> applicationProperties) {
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
        if (isLocal(env)) {
            env.enableCheckpointing(60000);
            env.setParallelism(1);
        }

        Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        Properties props = selectApplicationProperties(applicationProperties);

        String streamArn = getRequiredProperty(props, "KINESIS_STREAM_ARN");
        String outputPath = normalizeOutputPath(getRequiredProperty(props, "CURATED_S3_PATH"));
        String awsRegion = props.getProperty("AWS_REGION", "ap-northeast-2");

        System.setProperty("aws.region", awsRegion);
        Configuration sourceConfig = new Configuration();
        sourceConfig.setString(AWSConfigConstants.AWS_REGION, awsRegion);
        sourceConfig.set(KinesisStreamsSourceConfigConstants.STREAM_INITIAL_POSITION,
            KinesisStreamsSourceConfigConstants.InitialPosition.LATEST);

        KinesisStreamsSource<String> source = KinesisStreamsSource.<String>builder()
            .setStreamArn(streamArn)
            .setSourceConfig(sourceConfig)
            .setDeserializationSchema(new SimpleStringSchema())
            .build();

        DataStream<HeatmapAggregate> aggregated = env
            .fromSource(source, WatermarkStrategy.noWatermarks(), "kinesis-source", Types.STRING)
            .flatMap((String json, Collector<HeatmapAggregate> out) -> {
                HeatmapAggregate agg = parseEvent(json);
                if (agg != null) {
                    out.collect(agg);
                }
            })
            .returns(HeatmapAggregate.class)
            .keyBy(HeatmapJob::keyOf)
            .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
            .reduce((a, b) -> {
                a.clicks += b.clicks;
                return a;
            }, new WindowEnricher());

        FileSink<HeatmapAggregate> sink = FileSink
            .forBulkFormat(new Path(outputPath), AvroParquetWriters.forReflectRecord(HeatmapAggregate.class))
            .withBucketAssigner(new DateTimeBucketAssigner<>("'dt='yyyy-MM-dd'/hour='HH", ZoneId.of("UTC")))
            .withRollingPolicy(OnCheckpointRollingPolicy.build())
            .withOutputFileConfig(OutputFileConfig.builder().withPartSuffix(".parquet").build())
            .build();

        aggregated.sinkTo(sink).name("parquet-sink");

        env.execute("heatmap-minimal-job");
    }

    private static HeatmapAggregate parseEvent(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!"click".equals(node.path("event_type").asText(""))) {
                return null;
            }
            if (!node.hasNonNull("x") || !node.hasNonNull("y")) {
                return null;
            }
            int viewportWidth = node.path("viewport_width").asInt(0);
            int viewportHeight = node.path("viewport_height").asInt(0);
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                return null;
            }

            int x = node.get("x").asInt();
            int y = node.get("y").asInt();

            double dpr = node.path("dpr").asDouble(1.0);
            double scaledWidth = viewportWidth * dpr;
            double scaledHeight = viewportHeight * dpr;

            int gridX = (int) Math.floor((x * dpr * GRID_SIZE) / scaledWidth);
            int gridY = (int) Math.floor((y * dpr * GRID_SIZE) / scaledHeight);
            gridX = clamp(gridX, 0, GRID_SIZE - 1);
            gridY = clamp(gridY, 0, GRID_SIZE - 1);

            HeatmapAggregate agg = new HeatmapAggregate();
            agg.pageId = node.path("page_id").asText("unknown");
            agg.gridX = gridX;
            agg.gridY = gridY;
            agg.clicks = 1L;
            return agg;
        } catch (Exception e) {
            return null;
        }
    }

    private static String keyOf(HeatmapAggregate agg) {
        return agg.pageId + "|" + agg.gridX + "|" + agg.gridY;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeOutputPath(String outputPath) {
        String path = outputPath.trim();
        if (!path.endsWith("/")) {
            path += "/";
        }
        if (!path.endsWith("curated_heatmap/")) {
            path += "curated_heatmap/";
        }
        return path;
    }

    private static class WindowEnricher extends ProcessWindowFunction<HeatmapAggregate, HeatmapAggregate, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<HeatmapAggregate> elements, Collector<HeatmapAggregate> out) {
            HeatmapAggregate agg = elements.iterator().next();
            agg.windowStart = context.window().getStart();
            agg.windowEnd = context.window().getEnd();
            out.collect(agg);
        }
    }
}
