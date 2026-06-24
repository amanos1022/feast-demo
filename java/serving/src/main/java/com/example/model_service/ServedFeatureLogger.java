package com.example.model_service;

import java.util.Map;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

@Component
public class ServedFeatureLogger {
  private static final Logger logger = Logger.getLogger(ServedFeatureLogger.class.getName());

  public void emit(String requestId, String userId, String trackId,
                   long requestTsEpoch, Map<String, Object> features, double pSkip) {
    // Log the served feature vector alongside the prediction.
    // In production this would write to Kafka/S3 for offline skew analysis.
    logger.info(String.format(
      "request_id=%s user_id=%s track_id=%s ts=%d p_skip=%.4f features=%s",
      requestId, userId, trackId, requestTsEpoch, pSkip, features
    ));
  }
}
