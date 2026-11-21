package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payment_record")
@Getter
@Setter
@NoArgsConstructor
public class PaymentRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private UUID packageId;

  @Column(nullable = false)
  private String requestKey;

  private long coins;
  private long amountMinor;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String provider;

  private String providerReference;

  @Column(nullable = false)
  private String status;

  private boolean test;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
