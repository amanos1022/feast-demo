package com.example.model_service;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PredictController {
  private final FeastReader feast;
  private final OnnxModel model;
  private final ServedFeatureLogger log;

  public PredictController(FeastReader feast, OnnxModel model, ServedFeatureLogger log) {
    this.feast = feast;
    this.model = model;
    this.log = log;
  }

  @PostMapping("/predict")
  public PredictResponse predict(@RequestBody PredictRequest r) {
    long nowEpoch = Instant.now().getEpochSecond();

    // online f eatures over gRPC
    Map<String,Object> online = feast.getOnlineFeatures(
      "skip_v1",
      Map.of(
        "user_id",
        r.userId(),
        "track_id",
        r.trackId(),
        "request_ts_epoch",
        nowEpoch
      )
    );

    float[] vec = model.assemble(online);
    double pSkip = model.predictProba(vec);
    log.emit(r.requestId(), r.userId(), r.trackId(), nowEpoch, online, pSkip);

    return new PredictResponse(r.requestId(), pSkip);
  }
  
}
