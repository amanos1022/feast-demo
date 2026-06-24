package com.music.common;

import com.fasterxml.jackson.databind.ObjectMapper;

public record Play(
    String eventId,
    String userId,
    String trackId,
    Genre genre,
    long eventTimestamp,
    int playedMs,
    int trackLenMs,
    boolean skipped,
    Device device) {
  public static Play from(String val, ObjectMapper mapper) throws Exception {
    return mapper.readValue(val, Play.class);
  }
}
