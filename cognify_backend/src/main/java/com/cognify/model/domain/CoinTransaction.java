package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "coin_transaction")
@Getter
@Setter
@NoArgsConstructor
public class CoinTransaction {
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
  private String kind;

  private long amount;
  private long reservedDelta;
  private long balanceAfter;
  private long reservedAfter;
  private UUID referenceId;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
