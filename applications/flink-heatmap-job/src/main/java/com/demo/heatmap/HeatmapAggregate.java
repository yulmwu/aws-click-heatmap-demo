package com.demo.heatmap;

import org.apache.avro.reflect.AvroName;

public class HeatmapAggregate {
    @AvroName("page_id")
    public String pageId;
    @AvroName("grid_x")
    public int gridX;
    @AvroName("grid_y")
    public int gridY;
    @AvroName("window_start")
    public long windowStart;
    @AvroName("window_end")
    public long windowEnd;
    @AvroName("clicks")
    public long clicks;
}
