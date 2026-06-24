package com.music.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class UserStatsProcessor implements FeatureProcessor {
  private static final String VIEW_NAME = "user_stats";
  private static final Set<String> OUTPUT_COLUMNS =
      new LinkedHashSet<>(
          Set.of(
              "user_id",
              "user_plays_7d",
              "user_skip_rate_7d",
              "user_avg_play_ms",
              "event_timestamp"));

  public CompletableFuture<Void> process(FeastRegistry registry) {
    String tableName = registry.getView(VIEW_NAME).tableName();
    registry.validateColumns(VIEW_NAME, OUTPUT_COLUMNS);

    return CompletableFuture.runAsync(
        () -> {
          String query =
              """
          WITH date_range AS (
            SELECT
              min(event_timestamp)::date + interval '7 days' AS start_dt,
              max(event_timestamp)::date AS end_dt
            FROM '%s'
          ),
          windows AS (
            SELECT unnest(generate_series(
              (SELECT start_dt FROM date_range),
              (SELECT end_dt FROM date_range),
              interval '1 day'
            )) AS window_end
          )
          SELECT
            r.user_id,
            CAST(count(*) AS BIGINT) AS user_plays_7d,
            CAST(avg(CASE WHEN r.skipped THEN 1.0 ELSE 0.0 END) AS FLOAT) AS user_skip_rate_7d,
            CAST(avg(r.played_ms) AS FLOAT) AS user_avg_play_ms,
            w.window_end AS event_timestamp
          FROM '%s' r
          CROSS JOIN windows w
          WHERE r.event_timestamp >= w.window_end - interval '7 days'
            AND r.event_timestamp < w.window_end
          GROUP BY r.user_id, w.window_end
          """
                  .formatted(ParquetQueryer.RAW_EVENTS, ParquetQueryer.RAW_EVENTS);

          try (Connection duck = ParquetQueryer.getDuckDbConnection();
              Statement duckStmt = duck.createStatement();
              ResultSet rs = duckStmt.executeQuery(query);
              Connection pg = ParquetQueryer.getPostgresConnection()) {

            pg.setAutoCommit(false);
            try (Statement truncate = pg.createStatement()) {
              truncate.execute("TRUNCATE " + tableName);
            }

            String insert =
                "INSERT INTO %s (user_id, user_plays_7d, user_skip_rate_7d, user_avg_play_ms, event_timestamp) VALUES (?, ?, ?, ?, ?)"
                    .formatted(tableName);
            try (PreparedStatement ps = pg.prepareStatement(insert)) {
              int batch = 0;
              while (rs.next()) {
                ps.setString(1, rs.getString("user_id"));
                ps.setLong(2, rs.getLong("user_plays_7d"));
                ps.setFloat(3, rs.getFloat("user_skip_rate_7d"));
                ps.setFloat(4, rs.getFloat("user_avg_play_ms"));
                ps.setTimestamp(5, rs.getTimestamp("event_timestamp"));
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
