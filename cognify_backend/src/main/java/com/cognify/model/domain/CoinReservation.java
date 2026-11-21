package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "coin_reservation")
@Getter
@Setter
@NoArgsConstructor
public class CoinReservation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String operation;

  @Column(nullable = false)
  private String requestKey;

  private long amount;
  private long settledAmount;
  private long refundedAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status = ReservationStatus.HELD;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
