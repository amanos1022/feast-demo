package com.example.model_service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OnnxModel {
  // Must match the column order from training:
  // X = training_df.drop(columns=["label", "user_id", "track_id", "event_timestamp"])
  // Feast returns features in feature-service order (user_stats, track_stats,
  // track_realtime, request_features), so this is the order.
  private static final List<String> FEATURE_ORDER = List.of(
      "user_skip_rate_7d",
      "user_plays_7d",
      "user_avg_play_ms",
      "track_lifetime_plays",
      "track_lifetime_skip_rate",
      "genre",
      "tempo",
      "track_plays_last_1h",
      "track_skips_last_1h",
      "hour_of_day",
      "is_weekend"
  );

  private final OrtEnvironment env;
  private final OrtSession session;

  public OnnxModel(@Value("${onnx.model.path}") String modelPath) throws OrtException {
    this.env = OrtEnvironment.getEnvironment();
    this.session = env.createSession(modelPath);
  }

  public float[] assemble(Map<String, Object> online) {
    float[] vec = new float[FEATURE_ORDER.size()];
    for (int i = 0; i < FEATURE_ORDER.size(); i++) {
      vec[i] = toFloat(online.get(FEATURE_ORDER.get(i)));
    }
    return vec;
  }

  public double predictProba(float[] vec) {
    try {
      float[][] input = new float[][] {vec};
      OnnxTensor tensor = OnnxTensor.createTensor(env, input);

      try (var results = session.run(Map.of("input", tensor))) {
        // XGBoost ONNX classifiers output: [0]=labels, [1]=probabilities
        float[][] probas = (float[][]) results.get(1).getValue();
        return probas[0][1]; // P(skip=1)
      }
    } catch (OrtException e) {
      throw new RuntimeException("ONNX inference failed", e);
    }
  }

  private float toFloat(Object val) {
    if (val instanceof Number n) return n.floatValue();
    if (val instanceof Boolean b) return b ? 1.0f : 0.0f;
    if (val == null) return 0.0f;
    throw new IllegalArgumentException("Cannot convert to float: " + val.getClass());
  }

  @PreDestroy
  public void close() throws OrtException {
    session.close();
  }
}
