
package com.demo.heatmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import java.time.Duration;

public class HeatmapJob {

    private static final int GRID_SIZE = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        String streamArn = System.getenv().getOrDefault("KINESIS_STREAM_ARN", "arn:aws:kinesis:us-east-1:123456789012:stream/demo");
        
        KinesisStreamsSource<String> source =
            KinesisStreamsSource.<String>builder()
                .setStreamArn(streamArn)
                .setDeserializationSchema(new SimpleStringSchema())
                .build();

        DataStream<ClickEvent> clicks =
            env.fromSource(
                    source,
                    WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((SerializableTimestampAssigner<String>) (json, recordTimestamp) -> {
                            try {
                                JsonNode node = MAPPER.readTree(json);
                                return node.has("event_time_ms") ? node.get("event_time_ms").asLong() : System.currentTimeMillis();
                            } catch (Exception e) {
                                return System.currentTimeMillis();
                            }
                        }),
                    "kinesis-source"
                )
                .map(json -> {
                    JsonNode node = MAPPER.readTree(json);
                    ClickEvent evt = new ClickEvent();
                    evt.pageId = node.has("page_id") ? node.get("page_id").asText() : "unknown";
                    evt.eventTime = node.has("event_time_ms") ? node.get("event_time_ms").asLong() : System.currentTimeMillis();
                    evt.x = node.has("x") ? node.get("x").asInt() : 0;
                    evt.y = node.has("y") ? node.get("y").asInt() : 0;
                    evt.viewportWidth = node.has("viewport_width") ? node.get("viewport_width").asInt() : 1920;
                    evt.viewportHeight = node.has("viewport_height") ? node.get("viewport_height").asInt() : 1080;
                    evt.dpr = node.has("dpr") ? node.get("dpr").asDouble() : 1.0;
                    return evt;
                })
                .assignTimestampsAndWatermarks(
                    WatermarkStrategy.<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((SerializableTimestampAssigner<ClickEvent>) (event, recordTimestamp) -> event.eventTime)
                );

        DataStream<HeatmapAggregate> aggregated =
            clicks
                .map(evt -> {
                    HeatmapAggregate agg = new HeatmapAggregate();
                    agg.pageId = evt.pageId;
                    agg.gridX = Math.min(GRID_SIZE - 1, (evt.x * GRID_SIZE) / Math.max(1, evt.viewportWidth));
                    agg.gridY = Math.min(GRID_SIZE - 1, (evt.y * GRID_SIZE) / Math.max(1, evt.viewportHeight));
                    agg.windowStart = 0;
                    agg.windowEnd = 0;
                    agg.clicks = 1;
                    return agg;
                })
                .keyBy(e -> e.pageId + "-" + e.gridX + "-" + e.gridY)
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .reduce((a, b) -> {
                    a.clicks += b.clicks;
                    return a;
                })
                .map(agg -> {
                    // Window information is lost in reduce, use current time as approximation
                    long now = System.currentTimeMillis();
                    agg.windowStart = now - 60000; // 1 minute ago
                    agg.windowEnd = now;
                    return agg;
                });

        String outputPath = System.getenv().getOrDefault("CURATED_S3_PATH", "s3://bucket/curated/");
        if (!outputPath.endsWith("/")) {
            outputPath += "/";
        }
        outputPath += "curated_heatmap/";
        
        // Define Avro schema for Parquet
        String schemaString = "{"
            + "\"type\":\"record\","
            + "\"name\":\"HeatmapAggregate\","
            + "\"namespace\":\"com.demo.heatmap\","
            + "\"fields\":["
            + "{\"name\":\"pageid\",\"type\":\"string\"},"
            + "{\"name\":\"gridx\",\"type\":\"int\"},"
            + "{\"name\":\"gridy\",\"type\":\"int\"},"
            + "{\"name\":\"windowstart\",\"type\":\"long\"},"
            + "{\"name\":\"windowend\",\"type\":\"long\"},"
            + "{\"name\":\"clicks\",\"type\":\"long\"}"
            + "]}";
        Schema avroSchema = new Schema.Parser().parse(schemaString);

        FileSink<GenericRecord> sink =
            FileSink
                .forBulkFormat(
                    new Path(outputPath),
                    AvroParquetWriters.forGenericRecord(avroSchema)
                )
                .withRollingPolicy(OnCheckpointRollingPolicy.build())
                .build();

        aggregated
            .map(agg -> {
                GenericRecord record = new GenericData.Record(avroSchema);
                record.put("pageid", agg.pageId);
                record.put("gridx", agg.gridX);
                record.put("gridy", agg.gridY);
                record.put("windowstart", agg.windowStart);
                record.put("windowend", agg.windowEnd);
                record.put("clicks", agg.clicks);
                return record;
            })
            .sinkTo(sink);

        env.execute("heatmap-flink-job");
    }
}
