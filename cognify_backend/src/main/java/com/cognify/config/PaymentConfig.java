package com.cognify.config;

import com.cognify.service.payment.PaymentProvider;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class PaymentConfig {
  @Bean
  public PaymentProvider paymentProvider(
      Environment env, @Value("${cognify.payments.test-enabled:false}") boolean enabled) {
    Set<String> profiles = Set.of(env.getActiveProfiles());
    if (enabled && (!profiles.contains("dev") || profiles.contains("prod")))
      throw new IllegalStateException("Test payments require dev and are forbidden in prod");
    return new PaymentProvider() {
      public String name() {
        return enabled ? "TEST" : "DISABLED";
      }

      public boolean available() {
        return enabled;
      }

      public boolean testOnly() {
        return enabled;
      }

      public Receipt create(UUID id, long amount, String currency) {
        if (!enabled)
          throw new ResponseStatusException(
              HttpStatus.SERVICE_UNAVAILABLE, "Payments are not configured");
        return new Receipt("test-" + id, true);
      }

      public boolean verifyNotification(byte[] body, String signature) {
        return false;
      }
    };
  }
}
