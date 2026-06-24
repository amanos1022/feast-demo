package com.music.stream;

public class TrackAgg {
  public String trackId;
  public long plays;
  public long skips;
  public String windowEnd;

  public TrackAgg() {}

  public TrackAgg(String trackId, long plays, long skips, String windowEnd) {

    this.trackId = trackId;
    this.plays = plays;
    this.skips = skips;
    this.windowEnd = windowEnd;
  }
}
