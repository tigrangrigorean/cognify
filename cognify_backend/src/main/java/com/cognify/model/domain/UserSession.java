package com.cognify.model.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "usersession")
@Getter
@Setter
@NoArgsConstructor
public class UserSession {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant lastUsedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private boolean revoked;
}
