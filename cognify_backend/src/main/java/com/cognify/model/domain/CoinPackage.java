package com.cognify.model.domain;

import com.cognify.model.vo.*;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "coin_package")
@Getter
@Setter
@NoArgsConstructor
public class CoinPackage {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  private long coins;
  private long amountMinor;

  @Column(nullable = false)
  private String currency = "AMD";

  private boolean active;
  @Version private long version;
}
