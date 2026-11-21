package com.cognify.service;

import com.cognify.service.ai.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "cognify.generation.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GenerationWorker {
  private final GenerationService jobs;
  private final AiProvider ai;

  @Scheduled(fixedDelay = 1500)
  public void tick() {
    var claim = jobs.claim();
    if (claim.isEmpty()) return;
    var c = claim.get();
    try {
      var result = c.stepIndex() < 0 ? ai.plan(c.request()) : ai.section(c.request());
      jobs.complete(c, result);
    } catch (AiFailure e) {
      jobs.fail(c, e.code());
    } catch (Exception e) {
      jobs.fail(c, "GENERATION_ERROR");
    }
  }
}
