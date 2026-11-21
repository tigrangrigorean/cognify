package com.cognify.model.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "generation_step")
@Getter
@Setter
@NoArgsConstructor
public class GenerationStep {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID jobId;

  private int position;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content = "";

  private boolean completed;
  private int attempts;
}
