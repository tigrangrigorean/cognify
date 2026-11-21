package com.cognify.model.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "ai_usage")
@Getter
@Setter
@NoArgsConstructor
public class AiUsage {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID jobId;

  private int stepIndex;

  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private String model;

  private long inputTokens;
  private long outputTokens;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
