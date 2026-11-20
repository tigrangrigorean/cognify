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
  private final RefreshSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public record AuthResult(String accessToken, String refreshToken, String name) {}

  @Transactional
  public AuthResult register(Register r) {
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
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
    return issue(a);
  }

  @Transactional
  public AuthResult login(Login r) {
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    Account a =
        accounts
            .findByEmail(r.email().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (!passwords.matches(r.password(), a.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return issue(a);
  }

  @Transactional
  public AuthResult refresh(String token) {
    RefreshSession s =
        sessions
            .lockToken(hash(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (s.isRevoked() || s.getExpiresAt().isBefore(Instant.now()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    s.setRevoked(true);
    return issue(accounts.findById(s.getOwnerId()).orElseThrow());
  }

  @Transactional
  public void logout(String token) {
    sessions.lockToken(hash(token)).ifPresent(s -> s.setRevoked(true));
  }

  private AuthResult issue(Account a) {
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
                        .build()))
            .getTokenValue();
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshSession s = new RefreshSession();
    s.setOwnerId(a.getId());
    s.setTokenHash(hash(token));
    s.setExpiresAt(now.plusSeconds(604800));
    sessions.save(s);
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
