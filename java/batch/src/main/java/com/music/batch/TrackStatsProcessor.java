package com.music.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TrackStatsProcessor implements FeatureProcessor {
  private static final String VIEW_NAME = "track_stats";
  private static final Set<String> OUTPUT_COLUMNS =
      new LinkedHashSet<>(
          Set.of(
              "track_id",
              "track_lifetime_plays",
              "track_lifetime_skip_rate",
              "genre",
              "tempo",
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
              min(event_timestamp)::date + interval '1 day' AS start_dt,
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
            r.track_id,
            CAST(count(*) AS BIGINT) AS track_lifetime_plays,
            CAST(avg(CASE WHEN r.skipped THEN 1.0 ELSE 0.0 END) AS FLOAT) AS track_lifetime_skip_rate,
            CAST(CASE mode(r.genre)
              WHEN 'classical' THEN 0
              WHEN 'electronic' THEN 1
              WHEN 'hip-hop' THEN 2
              WHEN 'jazz' THEN 3
              WHEN 'pop' THEN 4
              WHEN 'rock' THEN 5
              ELSE -1
            END AS INTEGER) AS genre,
            CAST(60.0 + abs(hash(r.track_id) %% 120) AS FLOAT) AS tempo,
            w.window_end AS event_timestamp
          FROM '%s' r
          CROSS JOIN windows w
          WHERE r.event_timestamp < w.window_end
          GROUP BY r.track_id, w.window_end
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
                "INSERT INTO %s (track_id, track_lifetime_plays, track_lifetime_skip_rate, genre, tempo, event_timestamp) VALUES (?, ?, ?, ?, ?, ?)"
                    .formatted(tableName);
            try (PreparedStatement ps = pg.prepareStatement(insert)) {
              int batch = 0;
              while (rs.next()) {
                ps.setString(1, rs.getString("track_id"));
                ps.setLong(2, rs.getLong("track_lifetime_plays"));
                ps.setFloat(3, rs.getFloat("track_lifetime_skip_rate"));
                ps.setInt(4, rs.getInt("genre"));
                ps.setFloat(5, rs.getFloat("tempo"));
                ps.setTimestamp(6, rs.getTimestamp("event_timestamp"));
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
