package com.cognify.service;

import com.cognify.model.domain.PasswordReset;
import com.cognify.model.dto.Requests.*;
import com.cognify.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
  private final AccountRepository accounts;
  private final PasswordResetRepository resets;
  private final UserSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final ResetMailService mail;

  @Transactional
  public void request(ResetRequest request) {
    mail.requireAvailable();
    accounts
        .findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
        .ifPresent(
            found -> {
              var account = accounts.lockById(found.getId()).orElseThrow();
              byte[] bytes = new byte[48];
              new SecureRandom().nextBytes(bytes);
              String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
              PasswordReset reset = new PasswordReset();
              reset.setOwnerId(account.getId());
              reset.setTokenHash(hash(raw));
              reset.setExpiresAt(Instant.now().plusSeconds(1800));
              resets.saveAndFlush(reset);
              mail.send(account.getEmail(), raw);
            });
  }

  @Transactional
  public void confirm(ResetConfirm request) {
    if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
    String hash = hash(request.token());
    UUID owner = resets.findOwner(hash).orElseThrow(this::invalid);
    var account = accounts.lockById(owner).orElseThrow(this::invalid);
    var token = resets.lockToken(hash).orElseThrow(this::invalid);
    if (token.isUsed() || !token.getExpiresAt().isAfter(Instant.now())) throw invalid();
    account.setPasswordHash(passwords.encode(request.newPassword()));
    resets.findByOwnerId(owner).forEach(r -> r.setUsed(true));
    sessions.findByOwnerIdOrderByCreatedAtDesc(owner).forEach(s -> s.setRevoked(true));
  }

  private ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Վերականգնման հղումն անվավեր է կամ ժամկետանց։");
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
