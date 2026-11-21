package com.cognify.model.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public class GenerationDtos {
  public record Start(
      @NotBlank @Size(max = 200) String topic,
      @NotNull @Pattern(regexp = "COURSEWORK|THESIS|ESSAY|REPORT") String kind,
      @NotNull @Pattern(regexp = "hy|ru|en") String language,
      @Min(1) @Max(40) int pages,
      @NotNull @Size(max = 200) String subject,
      @NotNull @Size(max = 200) String university,
      @NotNull @Size(max = 5000) String requirements,
      @Min(1) long acceptedQuote,
      @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") @NotNull String requestKey) {}

  public record Plan(
      @NotNull @Size(min = 3, max = 12) List<@NotBlank @Size(max = 200) String> headings,
      @Min(0) long version) {}

  public record JobView(
      UUID id,
      String topic,
      String kind,
      String language,
      int pages,
      String status,
      String phase,
      List<String> plan,
      int completedSections,
      int totalSections,
      long quotedCoins,
      long chargedCoins,
      String provider,
      String model,
      String errorCode,
      UUID documentId,
      long version,
      Instant createdAt) {}

  public record Quote(int pages, long coins, String unit, String explanation) {}

  public record Config(
      boolean available, boolean mock, String provider, String model, int maxPages) {}

  public record SectionInput(
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 16000) String content,
      @Min(0) long version) {}

  public record Order(@NotNull @Size(max = 30) List<UUID> ids, @Min(0) long version) {}

  public record SectionView(UUID id, int position, String title, String content) {}

  public record VersionView(
      UUID id, long revision, String title, String content, Instant createdAt) {}
}
