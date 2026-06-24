package com.music.batch;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BatchJobRawEventsProcessor {
  private static final String REGISTRY_URL =
      System.getenv().getOrDefault("FEAST_REGISTRY_URL", "http://localhost:8000");
  private static final String PROJECT = System.getenv().getOrDefault("FEAST_PROJECT", "music");
  private static final String FEATURE_SERVICE =
      System.getenv().getOrDefault("FEAST_FEATURE_SERVICE", "skip_v1");

  static class MissingRequiredViewsException extends Exception {
    public MissingRequiredViewsException(String message) {
      super(message);
    }
  }

  public static void main(String[] args) throws Exception {
    FeastRegistry registry = new FeastRegistry(REGISTRY_URL, PROJECT, FEATURE_SERVICE);

    Map<String, FeatureProcessor> processors =
        Map.of(
            "user_stats", new UserStatsProcessor(),
            "track_stats", new TrackStatsProcessor(),
            "track_realtime", new TrackRealtimeBatchProcessor());

    try {
      Set<String> requiredViews = getRequiredViews(registry, processors);

      var processorFutures =
          requiredViews.stream()
              .map(processors::get)
              .map(processor -> processor.process(registry))
              .toArray(CompletableFuture[]::new);

      CompletableFuture.allOf(processorFutures).join();
    } catch (MissingRequiredViewsException e) {
      throw new RuntimeException(e.getMessage());
    } catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static Set<String> getRequiredViews(
      FeastRegistry registry, Map<String, FeatureProcessor> processors)
      throws MissingRequiredViewsException {
    Set<String> requiredViews = registry.getViewNames();

    for (String view : requiredViews) {
      if (!processors.containsKey(view)) {
        var errorMsg =
            "Feature service '%s' requires view '%s' but no processor is registered for it"
                .formatted(FEATURE_SERVICE, view);
        throw new MissingRequiredViewsException(errorMsg);
      }
    }

    return requiredViews;
  }
}
