package com.music.common;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Genre {
  SYNTHWAVE("synthwave"),
  JAZZ("jazz"),
  TECHNO("techno"),
  AMBIENT("ambient"),
  POP("pop");

  private final String value;

  Genre(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
