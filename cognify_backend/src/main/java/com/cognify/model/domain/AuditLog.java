package com.cognify.model.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID actorId;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String target;

  @Column(columnDefinition = "text")
  private String beforeValue;

  @Column(columnDefinition = "text")
  private String afterValue;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
