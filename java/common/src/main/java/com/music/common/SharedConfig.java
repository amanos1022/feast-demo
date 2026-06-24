package com.music.common;

public class SharedConfig {
  private SharedConfig() {
    /* This utility class should not be instantiated */
  }

  public static final String KAFKA_HOST = "127.0.0.1:9094";
  public static final String KAFKA_TOPIC = "plays";
  public static final String FEAST_PUSH = "http://feast-feature-server.feast:6566/push";

  public static final String S3_ENDPOINT = env("AWS_ENDPOINT_URL", "http://localhost:9000");
  public static final String AWS_ACCESS_KEY_ID = env("AWS_ACCESS_KEY_ID", "minio");
  public static final String AWS_SECRET_ACCESS_KEY = env("AWS_SECRET_ACCESS_KEY", "minio12345");
  public static final String AWS_DEFAULT_REGION = env("AWS_DEFAULT_REGION", "us-east-1");
  public static final String FEAST_BUCKET = env("FEAST_BUCKET", "feast");

  public static final String PG_HOST = env("PG_HOST", "localhost");
  public static final int PG_PORT = Integer.parseInt(env("PG_PORT", "5432"));
  public static final String PG_DB = env("PG_DB", "feast");
  public static final String PG_USER = env("PG_USER", "feast");
  public static final String PG_PASS = env("PG_PASS", "feast");

  private static String env(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null && !value.isEmpty() ? value : defaultValue;
  }
}
