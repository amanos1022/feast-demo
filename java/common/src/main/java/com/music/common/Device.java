package com.music.common;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Device {
  ANDROID("android"),
  IOS("ios");

  private final String value;

  Device(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
