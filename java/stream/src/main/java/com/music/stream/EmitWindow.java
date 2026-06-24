package com.music.stream;

import java.time.Instant;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class EmitWindow implements WindowFunction<Counts, TrackAgg, String, TimeWindow> {
  @Override
  public void apply(
    String key,
    TimeWindow window,
    Iterable<Counts> input,
    Collector<TrackAgg> out
  ) {
    Counts counts = input.iterator().next();

    out.collect(
      new TrackAgg(
        key, 
        counts.plays,
        counts.skips,
        Instant.ofEpochMilli(window.getEnd()).toString()
      )
    );
  }
}
