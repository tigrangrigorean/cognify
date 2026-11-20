package com.cognify.api.security;

import com.cognify.repository.UserSessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionTokenValidator implements OAuth2TokenValidator<Jwt> {
  private final UserSessionRepository sessions;

  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    try {
      var s =
          sessions.findByIdAndOwnerId(
              UUID.fromString(jwt.getClaimAsString("sid")), UUID.fromString(jwt.getSubject()));
      if (s.isPresent() && !s.get().isRevoked() && s.get().getExpiresAt().isAfter(Instant.now()))
        return OAuth2TokenValidatorResult.success();
    } catch (IllegalArgumentException | NullPointerException ignored) {
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Session is expired or revoked", null));
  }
}
