package com.music.batch;

import com.music.common.SharedConfig;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ParquetQueryer {
  public static final String RAW_EVENTS =
      "s3://" + SharedConfig.FEAST_BUCKET + "/raw/plays.parquet";

  public static Connection getDuckDbConnection() throws Exception {
    URI endpoint = URI.create(SharedConfig.S3_ENDPOINT);
    Connection conn = DriverManager.getConnection("jdbc:duckdb:");
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("INSTALL httpfs");
      stmt.execute("LOAD httpfs");
      stmt.execute("SET s3_endpoint='" + endpoint.getHost() + ":" + endpoint.getPort() + "'");
      stmt.execute("SET s3_access_key_id='" + SharedConfig.AWS_ACCESS_KEY_ID + "'");
      stmt.execute("SET s3_secret_access_key='" + SharedConfig.AWS_SECRET_ACCESS_KEY + "'");
      stmt.execute("SET s3_use_ssl=" + (endpoint.getScheme().equals("https") ? "true" : "false"));
      stmt.execute("SET s3_url_style='path'");
    }
    return conn;
  }

  public static Connection getPostgresConnection() throws Exception {
    String url = "jdbc:postgresql://%s:%d/%s".formatted(
        SharedConfig.PG_HOST, SharedConfig.PG_PORT, SharedConfig.PG_DB);
    return DriverManager.getConnection(url, SharedConfig.PG_USER, SharedConfig.PG_PASS);
  }
}
