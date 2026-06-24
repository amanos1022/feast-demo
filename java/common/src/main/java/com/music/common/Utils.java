package com.music.common;

public final class Utils {
  private Utils() {}

  public static String env(String key, String dflt) {
    var val = System.getenv(key);

    if (val == null || val.isBlank()) {
      return dflt;
    }

    return val;
  }
}
