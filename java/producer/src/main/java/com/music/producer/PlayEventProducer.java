package com.music.producer;

import java.time.Instant;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.music.common.SharedConfig;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;


public class PlayEventProducer {
  static final String[] USERS  = gen("u_", 200);
  static final String[] TRACKS = gen("t_", 500);
  static final String[] GENRES = {"synthwave","jazz","techno","ambient","pop"};

  public static void main(String[] args) {
    var props = new Properties();
    props.put("bootstrap.servers", SharedConfig.KAFKA_HOST);
    props.put("key.serializer",   StringSerializer.class.getName());
    props.put("value.serializer", StringSerializer.class.getName());
    var mapper = new ObjectMapper();
    var rnd = new Random();
    var k = 0;

    try (var producer = new KafkaProducer<String,String>(props)) {
      while (k < Integer.MAX_VALUE) {
        String track = TRACKS[rnd.nextInt(TRACKS.length)];
        int trackLen = 120_000 + rnd.nextInt(180_000);
        boolean skip = rnd.nextDouble() < skipPrior(track);
        int played = skip ? rnd.nextInt(30_000) : 30_000 + rnd.nextInt(trackLen - 30_000);

        var ev = mapper.createObjectNode();
        ev.put("event_id", UUID.randomUUID().toString());
        ev.put("user_id",  USERS[rnd.nextInt(USERS.length)]);
        ev.put("track_id", track);
        ev.put("genre",    GENRES[Math.abs(track.hashCode()) % GENRES.length]);
        ev.put("event_timestamp", Instant.now().toString());
        ev.put("played_ms", played);
        ev.put("track_len_ms", trackLen);
        ev.put("skipped", skip);
        ev.put("device", rnd.nextBoolean() ? "android" : "ios");

        producer.send(new ProducerRecord<>(
          SharedConfig.KAFKA_TOPIC, 
          track,
          mapper.writeValueAsString(ev)
        ));

        Thread.sleep(20); // ~50 events per second
        k++;
      }
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  static double skipPrior(String t){ 
    return 0.2 + 0.6 * (Math.abs(t.hashCode() % 100) / 100.0); 
  }

  static String[] gen(String prefix, int count) {
    return IntStream.range(0, count).mapToObj(
      i -> prefix + String.format(
        "%s%04d",
        prefix,
        Math.abs( // str.hasCode() can be negative
          (prefix + i).hashCode() // numeric code
        ) % 10000) // mod 1000 bounds the value returned from hasCode between 0 and 9999
      ).toArray(String[]::new);
  }
}