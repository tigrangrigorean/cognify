package com.cognify.model.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "cmscontent")
@Getter
@Setter
@NoArgsConstructor
public class CmsContent {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String contentKey;

  @Column(name = "content_value", columnDefinition = "text", nullable = false)
  private String value;
}
