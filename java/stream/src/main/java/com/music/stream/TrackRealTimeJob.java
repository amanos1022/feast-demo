package com.music.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.common.Play;
import com.music.common.SharedConfig;
import java.time.Duration;
import java.time.Instant;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

/** Watches the event stream so it can keep track plays and skips together */
public class TrackRealTimeJob {
  private StreamExecutionEnvironment flinkEnv;
  private KafkaSource<String> kafkaSource;
  private final ObjectMapper mapper = new ObjectMapper();

  public TrackRealTimeJob() {
    flinkEnv = StreamExecutionEnvironment.getExecutionEnvironment();
    kafkaSource =
        KafkaSource.<String>builder()
            .setBootstrapServers(SharedConfig.KAFKA_HOST)
            .setTopics("plays")
            .setGroupId(
                "flink-track-rt") // for flink to deterministically know what JobManager+TaskManager to put accumulator on
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(
                // kafka message payload is a simple string (albeit a json string in struct of Play).
                // you could do more with different class, as long as that
                // class implements DeserializationSchema<OUT>
                // i.e. You could skip the intermediate String by writing a custom DeserializationSchema<Play>
                new SimpleStringSchema()) 
            .build();
  }

  public void start() {
    // Read raw JSON strings from plays topic
    DataStream<Play> plays =
        this.flinkEnv.fromSource(
            this.kafkaSource,
            // watermark strategy tells fink, give a delay before processing window
            // so if we're processing 11:59 - 12:00 an event for 11:59:59 might come in at 12:00:25
            // with the 30s process delay, it still gets put in the window.
            // This is for processing of real-time data, if you wanted to back fill
            // events from a dead mobile app, you'd do a separate batch backfill job.
            // TODO: Write bounded flink batch job for backfilling
            WatermarkStrategy.<String>forBoundedOutOfOrderness( // when watermark passes, that's when emmit happens
              Duration.ofSeconds(30) // maxOutOfOrderness 
            ).withTimestampAssigner(
              (s, ts) -> this.parseTimestamp(s) // sets event timestamp from JSON `event_timestamp` filed
            ),
            "plays"
        ).map(s -> Play.from(s, mapper));

    // Actually performing the aggregation on the stream and pushing it to Feast (which fans out to
    // Redis and S3/parquet)
    plays
        .keyBy(Play::trackId) // play -> play.trackId()
        // This is just saying every minute, agg the last hour of data. 
        // delayed by 30s watermark for late events (configured above)
        // TODO: sliding window more expensive than a tumbling window.
        .window(SlidingEventTimeWindows.of(Duration.ofHours(1), Duration.ofMinutes(1))) // agg over 1 hour
        .aggregate(
            new CountAgg(), // folds
            new EmitWindow() // labels, runs once per window - hour->minute - on already produced result
            )
        .map(
            new FeastPushFunction( // chat says this is wrong should include flinkEnv.execute("track-realtime-job");
                SharedConfig
                    .FEAST_PUSH)); // writes. push to feast which writes to the online store.
  }

  private Long parseTimestamp(String json) {
    try {
      return Instant.parse(mapper.readTree(json).get("event_timestamp").asText()).toEpochMilli();
    } catch (Exception e) {
      return 0L;
    }
  }

  public static void main(String[] args) {
    var trackRealTimeJob = new TrackRealTimeJob();
    trackRealTimeJob.start();
  }
}
