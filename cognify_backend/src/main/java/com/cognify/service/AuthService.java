package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.Requests.*;
import com.cognify.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final AccountRepository accounts;
  private final WalletRepository wallets;
  private final RefreshSessionRepository tokens;
  private final UserSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public record AuthResult(String accessToken, String refreshToken, String name) {}

  public record SessionView(
      UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt, boolean current) {}

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  private void validatePassword(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
  }

  @Transactional
  public AuthResult register(Register r) {
    validatePassword(r.password());
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    if (accounts.findByEmail(email).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Account unavailable");
    Account a = new Account();
    a.setEmail(email);
    a.setName(r.name());
    a.setPasswordHash(passwords.encode(r.password()));
    accounts.save(a);
    Wallet w = new Wallet();
    w.setOwnerId(a.getId());
    wallets.save(w);
    return newSession(a);
  }

  @Transactional
  public AuthResult login(Login r) {
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72) throw unauthorized();
    Account found =
        accounts
            .findByEmail(r.email().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(this::unauthorized);
    Account a = accounts.lockById(found.getId()).orElseThrow(this::unauthorized);
    if (!passwords.matches(r.password(), a.getPasswordHash())) throw unauthorized();
    return newSession(a);
  }

  // All mutations lock the account first, serializing login, rotation and revocation.
  // Replay revocation must commit even though the request returns 401.
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public AuthResult refresh(String token) {
    UUID owner = tokens.findOwnerByTokenHash(hash(token)).orElseThrow(this::unauthorized);
    Account a = accounts.lockById(owner).orElseThrow(this::unauthorized);
    RefreshSession t = tokens.lockToken(hash(token)).orElseThrow(this::unauthorized);
    if (t.getSessionId() == null) throw unauthorized();
    UserSession s =
        sessions.findByIdAndOwnerId(t.getSessionId(), a.getId()).orElseThrow(this::unauthorized);
    if (t.isRevoked()) {
      s.setRevoked(true);
      throw unauthorized();
    }
    if (s.isRevoked()
        || !s.getExpiresAt().isAfter(Instant.now())
        || !t.getExpiresAt().isAfter(Instant.now())) throw unauthorized();
    t.setRevoked(true);
    s.setLastUsedAt(Instant.now());
    return issue(a, s);
  }

  @Transactional
  public void logout(String token) {
    tokens
        .findByTokenHash(hash(token))
        .ifPresent(
            t -> {
              accounts.lockById(t.getOwnerId()).orElseThrow();
              if (t.getSessionId() != null)
                sessions.findById(t.getSessionId()).ifPresent(s -> s.setRevoked(true));
            });
  }

  @Transactional(readOnly = true)
  public List<SessionView> listSessions(UUID owner, UUID current) {
    return sessions.findByOwnerIdOrderByCreatedAtDesc(owner).stream()
        .filter(s -> !s.isRevoked() && s.getExpiresAt().isAfter(Instant.now()))
        .map(
            s ->
                new SessionView(
                    s.getId(),
                    s.getCreatedAt(),
                    s.getLastUsedAt(),
                    s.getExpiresAt(),
                    s.getId().equals(current)))
        .toList();
  }

  @Transactional
  public void revoke(UUID owner, UUID id) {
    accounts.lockById(owner).orElseThrow(this::unauthorized);
    sessions
        .findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
        .setRevoked(true);
  }

  @Transactional
  public void revokeAll(UUID owner) {
    accounts.lockById(owner).orElseThrow(this::unauthorized);
    sessions.findByOwnerIdOrderByCreatedAtDesc(owner).forEach(s -> s.setRevoked(true));
  }

  @Transactional
  public void changePassword(UUID owner, PasswordChange r) {
    validatePassword(r.newPassword());
    Account a = accounts.lockById(owner).orElseThrow(this::unauthorized);
    if (r.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(r.currentPassword(), a.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    a.setPasswordHash(passwords.encode(r.newPassword()));
    sessions.findByOwnerIdOrderByCreatedAtDesc(owner).forEach(s -> s.setRevoked(true));
  }

  private AuthResult newSession(Account a) {
    Instant now = Instant.now();
    UserSession s = new UserSession();
    s.setOwnerId(a.getId());
    s.setCreatedAt(now);
    s.setLastUsedAt(now);
    s.setExpiresAt(now.plusSeconds(604800));
    sessions.save(s);
    return issue(a, s);
  }

  private AuthResult issue(Account a, UserSession session) {
    Instant now = Instant.now();
    String jwt =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder()
                        .subject(a.getId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(900))
                        .claim("scope", a.getRole())
                        .claim("sid", session.getId().toString())
                        .build()))
            .getTokenValue();
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshSession t = new RefreshSession();
    t.setOwnerId(a.getId());
    t.setSessionId(session.getId());
    t.setTokenHash(hash(token));
    t.setExpiresAt(session.getExpiresAt());
    tokens.save(t);
    return new AuthResult(jwt, token, a.getName());
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
