package com.cognify.model.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  private long revision;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
