package com.music.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TrackRealtimeBatchProcessor implements FeatureProcessor {
  private static final String VIEW_NAME = "track_realtime";
  private static final Set<String> OUTPUT_COLUMNS =
      new LinkedHashSet<>(
          Set.of("track_id", "track_plays_last_1h", "track_skips_last_1h", "event_timestamp"));

  public CompletableFuture<Void> process(FeastRegistry registry) {
    String tableName = registry.getView(VIEW_NAME).tableName();
    registry.validateColumns(VIEW_NAME, OUTPUT_COLUMNS);

    return CompletableFuture.runAsync(
        () -> {
          String query =
              """
          SELECT
            track_id,
            CAST(count(*) AS BIGINT) AS track_plays_last_1h,
            CAST(count(*) FILTER (WHERE skipped) AS BIGINT) AS track_skips_last_1h,
            time_bucket(interval '1 hour', event_timestamp) + interval '1 hour' AS event_timestamp
          FROM '%s'
          GROUP BY track_id, time_bucket(interval '1 hour', event_timestamp)
          """
                  .formatted(ParquetQueryer.RAW_EVENTS);

          try (Connection duck = ParquetQueryer.getDuckDbConnection();
              Statement duckStmt = duck.createStatement();
              ResultSet rs = duckStmt.executeQuery(query);
              Connection pg = ParquetQueryer.getPostgresConnection()) {

            pg.setAutoCommit(false);
            try (Statement truncate = pg.createStatement()) {
              truncate.execute("TRUNCATE " + tableName);
            }

            String insert =
                "INSERT INTO %s (track_id, track_plays_last_1h, track_skips_last_1h, event_timestamp) VALUES (?, ?, ?, ?)"
                    .formatted(tableName);
            try (PreparedStatement ps = pg.prepareStatement(insert)) {
              int batch = 0;
              while (rs.next()) {
                ps.setString(1, rs.getString("track_id"));
                ps.setLong(2, rs.getLong("track_plays_last_1h"));
                ps.setLong(3, rs.getLong("track_skips_last_1h"));
                ps.setTimestamp(4, rs.getTimestamp("event_timestamp"));
                ps.addBatch();
                if (++batch % 10_000 == 0) ps.executeBatch();
              }
              ps.executeBatch();
            }
            pg.commit();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
