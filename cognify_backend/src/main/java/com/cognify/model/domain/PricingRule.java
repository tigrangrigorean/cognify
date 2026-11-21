package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "pricing_rule")
@Getter
@Setter
@NoArgsConstructor
public class PricingRule {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String operation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PricingUnit unit;

  private long unitCost;
  private boolean active;
  @Version private long version;
}
