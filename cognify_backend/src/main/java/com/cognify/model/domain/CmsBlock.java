package com.cognify.model.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "cms_block")
@Getter
@Setter
@NoArgsConstructor
public class CmsBlock {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String blockKey;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false, columnDefinition = "text")
  private String itemsJson = "[]";

  private int position;
  private boolean visible = true;
  @Version private long version;
}
