package com.cognify.api.controller;

import com.cognify.model.dto.Requests.PasswordChange;
import com.cognify.service.AuthService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class SessionController {
  private final AuthService auth;

  @GetMapping("/sessions")
  public List<AuthService.SessionView> sessions(@AuthenticationPrincipal Jwt jwt) {
    return auth.listSessions(
        UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getClaimAsString("sid")));
  }

  @DeleteMapping("/sessions/{id}")
  public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    auth.revoke(UUID.fromString(jwt.getSubject()), id);
  }

  @DeleteMapping("/sessions")
  public void revokeAll(@AuthenticationPrincipal Jwt jwt) {
    auth.revokeAll(UUID.fromString(jwt.getSubject()));
  }

  @PostMapping("/password")
  public void password(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange request) {
    auth.changePassword(UUID.fromString(jwt.getSubject()), request);
  }
}
