package com.cognify.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public class CmsDtos {
  public record Item(
      @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 3000) String body) {}

  public record BlockInput(
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 10000) String body,
      @NotNull @Size(max = 20) List<@Valid Item> items,
      @Min(0) @Max(1000) int position,
      boolean visible,
      @Min(0) long version) {}

  public record BlockView(
      String key,
      String title,
      String body,
      List<Item> items,
      int position,
      boolean visible,
      long version) {}

  public record AuditView(
      UUID id,
      UUID actorId,
      String action,
      String target,
      String beforeValue,
      String afterValue,
      Instant createdAt) {}
}
