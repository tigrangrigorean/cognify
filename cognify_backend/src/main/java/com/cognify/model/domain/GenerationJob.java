package com.cognify.model.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "generation_job")
@Getter
@Setter
@NoArgsConstructor
public class GenerationJob {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String requestKey;

  @Column(nullable = false)
  private String fingerprint;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false)
  private String kind;

  @Column(nullable = false)
  private String language;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false)
  private String university;

  @Column(nullable = false, columnDefinition = "text")
  private String requirements;

  private int pages;

  @Column(nullable = false)
  private String status = "QUEUED";

  @Column(nullable = false)
  private String phase = "PLAN";

  @Column(nullable = false, columnDefinition = "text")
  private String planJson = "[]";

  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private String model;

  private long quotedCoins;
  private long unitCost;
  private long chargedCoins;
  private UUID reservationId;
  private int billingAttempt;
  private int planAttempts;
  private UUID leaseToken;
  private Instant leaseUntil;
  private String errorCode;
  private UUID documentId;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  @Version private long version;
}
