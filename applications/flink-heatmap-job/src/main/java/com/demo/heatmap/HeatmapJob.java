
package com.demo.heatmap;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import java.time.Duration;

public class HeatmapJob {

    private static final int GRID_SIZE = 20;

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        // Simple Kinesis source configuration
        String streamArn = System.getenv().getOrDefault("KINESIS_STREAM_ARN", "arn:aws:kinesis:us-east-1:123456789012:stream/demo");
        
        KinesisStreamsSource<String> source =
            KinesisStreamsSource.<String>builder()
                .setStreamArn(streamArn)
                .setDeserializationSchema(new SimpleStringSchema())
                .build();

        DataStream<HeatmapAggregate> aggregated =
            env.fromSource(
                    source,
                    WatermarkStrategy.noWatermarks(),
                    "kinesis-source"
                )
                .map(json -> {
                    // Simple parsing - in production, use proper JSON parsing
                    HeatmapAggregate out = new HeatmapAggregate();
                    out.pageId = "demo";
                    out.gridX = 0;
                    out.gridY = 0;
                    out.clicks = 1;
                    out.windowStart = System.currentTimeMillis();
                    out.windowEnd = out.windowStart;
                    return out;
                })
                .keyBy(e -> e.pageId + "-" + e.gridX + "-" + e.gridY)
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .reduce((a, b) -> {
                    a.clicks += b.clicks;
                    return a;
                });

        // File sink configuration
        String outputPath = System.getenv().getOrDefault("CURATED_S3_PATH", "s3://bucket/curated/");
        
        FileSink<HeatmapAggregate> sink =
            FileSink
                .forRowFormat(
                    new Path(outputPath),
                    new SimpleStringEncoder<HeatmapAggregate>()
                )
                .withRollingPolicy(
                    DefaultRollingPolicy.builder()
                        .withRolloverInterval(Duration.ofMinutes(5))
                        .build()
                )
                .build();

        aggregated.sinkTo(sink);
        env.execute("heatmap-flink-job");
    }
}
