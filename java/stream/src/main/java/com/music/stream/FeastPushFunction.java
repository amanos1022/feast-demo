package com.music.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;

public class FeastPushFunction extends RichMapFunction<TrackAgg, TrackAgg> {
  private final String endpoint;

  // feast serializes the instance of FeastPushFunction and sends it to other workers
  // transient keyword tells the serializer to not serialize these fields (because they're
  // unserializable i.e. don't implement Serializable)
  private transient ObjectMapper mapper;
  private transient HttpClient client;

  public FeastPushFunction(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  // This is for serialization concerns mentioned above as well.
  public void open(OpenContext ctx) {
    this.mapper = new ObjectMapper();
    this.client = HttpClient.newHttpClient();
  }

  @Override
  public TrackAgg map(TrackAgg value) throws Exception {
    ObjectNode body = mapper.createObjectNode();

    body.put("push_source_name", "track_realtime_push");

    ObjectNode df = mapper.createObjectNode();
    df.set("track_id", arrayOf(value.trackId));
    df.set("track_plays_last_1h", arrayOf(value.plays));
    df.set("track_skips_last_1h", arrayOf(value.skips));
    df.set("event_timestamp", arrayOf(value.windowEnd));
    body.set("df", df);

    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

    client.send(req, HttpResponse.BodyHandlers.discarding());

    return value;
  }

  private ArrayNode arrayOf(Object value) {
    ArrayNode arr = mapper.createArrayNode();

    if (value instanceof String s) {
      arr.add(s);
    } else if (value instanceof Long l) {
      arr.add(l);
    } else {
      arr.add(value.toString());
    }

    return arr;
  }
}
