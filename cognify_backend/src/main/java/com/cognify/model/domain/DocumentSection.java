package com.cognify.model.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "document_section")
@Getter
@Setter
@NoArgsConstructor
public class DocumentSection {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  private int position;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content = "";
}
