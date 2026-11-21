package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "usage_record")
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private UUID reservationId;

  @Column(nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String operation;

  private long coins;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
