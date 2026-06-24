package com.example.model_service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FeastReader {
  private final RestClient client;

  public FeastReader(
    @Value("${feast.serving.host}") String host,
    @Value("${feast.serving.port}") int port) {
    this.client = RestClient.builder()
      .baseUrl("http://" + host + ":" + port)
      .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
      .build();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getOnlineFeatures(String featureService, Map<String, Object> entities) {
    Map<String, Object> body = new HashMap<>();
    body.put("feature_service", featureService);

    Map<String, List<Object>> entityMap = new HashMap<>();
    for (var entry : entities.entrySet()) {
      entityMap.put(entry.getKey(), List.of(entry.getValue()));
    }
    body.put("entities", entityMap);

    Map<String, Object> response = client.post()
      .uri("/get-online-features")
      .contentType(MediaType.APPLICATION_JSON)
      .body(body)
      .retrieve()
      .body(Map.class);

    Map<String, Object> result = new HashMap<>();
    var metadata = (Map<String, Object>) response.get("metadata");
    var featureNames = (List<String>) metadata.get("feature_names");
    var results = (List<Map<String, Object>>) response.get("results");

    for (int i = 0; i < featureNames.size(); i++) {
      var values = (List<Object>) results.get(i).get("values");
      result.put(featureNames.get(i), values.get(0));
    }

    return result;
  }
}
