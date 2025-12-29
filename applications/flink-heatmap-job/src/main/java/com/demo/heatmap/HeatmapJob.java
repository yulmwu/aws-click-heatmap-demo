package com.demo.heatmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

public class HeatmapJob {

    private static final int GRID_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Helper to get property from environment variables or system properties.
     * MSF passes FlinkApplicationProperties as both system properties and env vars.
     * Try system property first, then environment variable.
     */
    private static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            value = System.getenv(key);
        }
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        // Read from environment variables (MSF passes via FlinkApplicationProperties)
        String streamArn = getProperty("KINESIS_STREAM_ARN", 
            "arn:aws:kinesis:us-east-1:123456789012:stream/demo");
        String awsRegion = getProperty("AWS_REGION", "ap-northeast-2");
        
        KinesisStreamsSource<String> source =
            KinesisStreamsSource.<String>builder()
                .setStreamArn(streamArn)
                .setDeserializationSchema(new SimpleStringSchema())
                .build();

        DataStream<HeatmapAggregate> aggregated =
            env.fromSource(source, WatermarkStrategy.noWatermarks(), "kinesis-source")
                .map(json -> {
                    try {
                        JsonNode node = MAPPER.readTree(json);
                        HeatmapAggregate agg = new HeatmapAggregate();
                        agg.pageId = node.has("page_id") ? node.get("page_id").asText() : "default";
                        
                        int x = node.has("x") ? node.get("x").asInt() : 0;
                        int y = node.has("y") ? node.get("y").asInt() : 0;
                        int vw = node.has("viewport_width") ? node.get("viewport_width").asInt() : 1920;
                        int vh = node.has("viewport_height") ? node.get("viewport_height").asInt() : 1080;
                        
                        agg.gridX = Math.min(GRID_SIZE - 1, (x * GRID_SIZE) / Math.max(1, vw));
                        agg.gridY = Math.min(GRID_SIZE - 1, (y * GRID_SIZE) / Math.max(1, vh));
                        agg.clicks = 1;
                        agg.windowStart = System.currentTimeMillis();
                        agg.windowEnd = System.currentTimeMillis();
                        return agg;
                    } catch (Exception e) {
                        // Skip invalid records
                        return null;
                    }
                })
                .filter(agg -> agg != null)
                .keyBy(agg -> agg.pageId + "-" + agg.gridX + "-" + agg.gridY)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
                .reduce((agg1, agg2) -> {
                    agg1.clicks += agg2.clicks;
                    agg1.windowEnd = Math.max(agg1.windowEnd, agg2.windowEnd);
                    return agg1;
                });

        String outputPath = getProperty("CURATED_S3_PATH", "s3://bucket/curated/");
        if (!outputPath.endsWith("/")) outputPath += "/";
        outputPath += "curated_heatmap/";
        
        Schema avroSchema = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"HeatmapAggregate\",\"namespace\":\"com.demo.heatmap\"," +
            "\"fields\":[" +
            "{\"name\":\"pageid\",\"type\":\"string\"}," +
            "{\"name\":\"gridx\",\"type\":\"int\"}," +
            "{\"name\":\"gridy\",\"type\":\"int\"}," +
            "{\"name\":\"windowstart\",\"type\":\"long\"}," +
            "{\"name\":\"windowend\",\"type\":\"long\"}," +
            "{\"name\":\"clicks\",\"type\":\"long\"}" +
            "]}"
        );

        FileSink<GenericRecord> sink = FileSink
            .forBulkFormat(new Path(outputPath), AvroParquetWriters.forGenericRecord(avroSchema))
            .withRollingPolicy(OnCheckpointRollingPolicy.build())
            .build();

        aggregated.map(agg -> {
            GenericRecord record = new GenericData.Record(avroSchema);
            record.put("pageid", agg.pageId);
            record.put("gridx", agg.gridX);
            record.put("gridy", agg.gridY);
            record.put("windowstart", agg.windowStart);
            record.put("windowend", agg.windowEnd);
            record.put("clicks", agg.clicks);
            return record;
        }).sinkTo(sink);

        env.execute("heatmap-flink-job");
    }
}
