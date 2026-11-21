package com.cognify.model.dto;

import com.cognify.model.vo.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public class CoinDtos {
  public record Balance(long balance, long reserved, long available) {}

  public record Entry(
      UUID id,
      String kind,
      long amount,
      long reservedDelta,
      long balanceAfter,
      long reservedAfter,
      UUID referenceId,
      String reason,
      Instant createdAt) {}

  public record Reservation(
      UUID id,
      String operation,
      long amount,
      long settledAmount,
      long refundedAmount,
      ReservationStatus status,
      Instant createdAt) {}

  public record Adjustment(
      @Min(-1000000000) @Max(1000000000) long amount,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String requestKey) {}

  public record Refund(
      @Min(1) @Max(1000000000) long amount,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String requestKey) {}

  public record PackageInput(
      @NotBlank @Size(max = 100) String name,
      @Min(1) @Max(1000000000) long coins,
      @Min(1) @Max(100000000000L) long amountMinor,
      @Pattern(regexp = "AMD") @NotNull String currency,
      boolean active,
      @Min(0) long version) {}

  public record PackageView(
      UUID id,
      String name,
      long coins,
      long amountMinor,
      String currency,
      boolean active,
      long version) {}

  public record RuleInput(
      @NotNull PricingUnit unit,
      @Min(1) @Max(1000000) long unitCost,
      boolean active,
      @Min(0) long version) {}

  public record RuleView(
      String operation, PricingUnit unit, long unitCost, boolean active, long version) {}

  public record Quote(String operation, PricingUnit unit, long units, long coins) {}

  public record PaymentInput(
      @NotNull UUID packageId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String requestKey) {}

  public record PaymentView(
      UUID id,
      UUID packageId,
      long coins,
      long amountMinor,
      String currency,
      String status,
      boolean test,
      Instant createdAt) {}

  public record PaymentMode(boolean available, boolean test, String message) {}
}
