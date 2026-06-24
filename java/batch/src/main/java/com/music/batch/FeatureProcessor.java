package com.music.batch;

import java.util.concurrent.CompletableFuture;

public interface FeatureProcessor {
  CompletableFuture<Void> process(FeastRegistry registry);
}
