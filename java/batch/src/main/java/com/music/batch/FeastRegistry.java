package com.music.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class FeastRegistry {
  private final Map<String, FeatureViewSchema> views = new HashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String featureServiceName;
  private final HttpClient client = HttpClient.newHttpClient();
  private final String registryUrl;
  private final String project;

  public record FeatureViewSchema(
      String name,
      String tableName,
      String timestampField,
      Set<String> entityColumns,
      Set<String> featureColumns) {
    public Set<String> expectedColumns() {
      Set<String> all = new LinkedHashSet<>();
      all.addAll(entityColumns);
      all.addAll(featureColumns);
      all.add(timestampField);
      return all;
    }
  }

  public FeastRegistry(String registryUrl, String project, String featureServiceName)
      throws Exception {
    this.featureServiceName = featureServiceName;
    this.registryUrl = registryUrl;
    this.project = project;
    // initializing the registry by hitting the registry url for the feast feature service e.g.
    // skip_v1
    // including request features and entity id's
    JsonNode serviceRoot =
        fetch(client, mapper, registryUrl + "/api/v1/feature_services?project=" + project);
    var serviceSpec = getFeatureServiceSpec(serviceRoot);

    var features = serviceSpec.get("features");

    for (JsonNode featureNode : features) {
      FeatureViewSchema schema = parseFeatureView(featureNode);
      if (schema != null) {
        views.put(schema.name(), schema);
      }
    }
  }

  private JsonNode getFeatureServiceSpec(JsonNode serviceRoot) throws Exception {
    var featureServices = serviceRoot.get("featureServices");
    for (JsonNode fs : featureServices) {
      if (featureServiceName.equals(fs.get("spec").get("name").asText())) {
        return fs.get("spec");
      }
    }

    throw new Exception(String.format("Could not find %s feature service.", featureServiceName));
  }

  private FeatureViewSchema parseFeatureView(JsonNode featureNode) throws Exception {
    String viewName = featureNode.get("featureViewName").asText();

    // On-demand views (like request_features) have no batchSource — skip them
    JsonNode batchSource = featureNode.get("batchSource");
    if (batchSource == null || batchSource.isNull()) {
      return null;
    }

    // PostgreSQLSource stores config as base64-encoded JSON in customOptions.configuration
    String tableName;
    JsonNode customOptions = batchSource.get("customOptions");
    if (customOptions != null && customOptions.has("configuration")) {
      String decoded = new String(
          Base64.getDecoder().decode(customOptions.get("configuration").asText()));
      JsonNode sourceConfig = mapper.readTree(decoded);
      // Use table name if set, otherwise derive from query "SELECT * FROM <table>"
      tableName = sourceConfig.has("table") && !sourceConfig.get("table").asText().isEmpty()
          ? sourceConfig.get("table").asText()
          : viewName;
    } else {
      tableName = viewName;
    }

    String timestampField = batchSource.get("timestampField").asText();

    Set<String> featureCols = new LinkedHashSet<>();
    for (JsonNode f : featureNode.get("featureColumns")) {
      featureCols.add(f.get("name").asText());
    }

    // Fetch entity join keys from the view and entity APIs
    JsonNode viewRoot =
        fetch(
            client,
            mapper,
            registryUrl + "/api/v1/feature_views/" + viewName + "?project=" + project);

    Set<String> entityCols = new LinkedHashSet<>();
    for (JsonNode entityName : viewRoot.get("spec").get("entities")) {
      JsonNode entityRoot =
          fetch(
              client,
              mapper,
              registryUrl + "/api/v1/entities/" + entityName.asText() + "?project=" + project);
      entityCols.add(entityRoot.get("spec").get("joinKey").asText());
    }

    return new FeatureViewSchema(viewName, tableName, timestampField, entityCols, featureCols);
  }

  public Set<String> getViewNames() {
    return Set.copyOf(views.keySet());
  }

  public FeatureViewSchema getView(String name) {
    FeatureViewSchema schema = views.get(name);

    if (schema == null) {
      throw new IllegalArgumentException(
          "Feature view '%s' not found in feature service '%s'"
              .formatted(name, featureServiceName));
    }

    return schema;
  }

  public void validateColumns(String viewName, Set<String> outputColumns) {
    FeatureViewSchema schema = getView(viewName);
    Set<String> expected = schema.expectedColumns();

    if (!expected.equals(outputColumns)) {
      Set<String> missing = new LinkedHashSet<>(expected);
      missing.removeAll(outputColumns);
      Set<String> unexpected = new LinkedHashSet<>(outputColumns);
      unexpected.removeAll(expected);

      throw new RuntimeException(
          "Schema mismatch for feature view '%s':\n  Expected: %s\n  Actual:   %s\n  Missing:  %s\n  Unexpected: %s"
              .formatted(viewName, expected, outputColumns, missing, unexpected));
    }
  }

  private static JsonNode fetch(HttpClient client, ObjectMapper mapper, String url)
      throws Exception {
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException(
          "Feast registry returned %d for %s".formatted(response.statusCode(), url));
    }
    return mapper.readTree(response.body());
  }
}
