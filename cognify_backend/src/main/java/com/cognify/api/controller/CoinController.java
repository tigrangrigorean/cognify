package com.cognify.api.controller;

import com.cognify.model.dto.CoinDtos.*;
import com.cognify.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CoinController {
  private final WalletService wallet;
  private final CoinCatalogService catalog;
  private final PaymentService payments;

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  @GetMapping("/wallet")
  public Balance balance(@AuthenticationPrincipal Jwt jwt) {
    return wallet.balance(owner(jwt));
  }

  @GetMapping("/wallet/transactions")
  public List<Entry> history(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    return wallet.transactions(owner(jwt), page);
  }

  @GetMapping("/wallet/reservations")
  public List<Reservation> reservations(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    return wallet.reservations(owner(jwt), page);
  }

  @GetMapping("/public/coin-packages")
  public List<PackageView> packages() {
    return catalog.packages(false);
  }

  @GetMapping("/public/payment-mode")
  public PaymentMode mode() {
    return payments.mode();
  }

  @GetMapping("/pricing/quote")
  public Quote quote(@RequestParam String operation, @RequestParam long units) {
    return catalog.quote(operation, units);
  }

  @GetMapping("/payments")
  public List<PaymentView> payments(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    return payments.list(owner(jwt), page);
  }

  @PostMapping("/payments")
  public PaymentView pay(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentInput input) {
    return payments.create(owner(jwt), input);
  }

  @GetMapping("/admin/coin-packages")
  public List<PackageView> adminPackages() {
    return catalog.packages(true);
  }

  @PostMapping("/admin/coin-packages")
  public PackageView create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PackageInput input) {
    return catalog.savePackage(owner(jwt), null, input);
  }

  @PutMapping("/admin/coin-packages/{id}")
  public PackageView update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody PackageInput input) {
    return catalog.savePackage(owner(jwt), id, input);
  }

  @GetMapping("/admin/pricing")
  public List<RuleView> rules() {
    return catalog.rules();
  }

  @PutMapping("/admin/pricing/{operation}")
  public RuleView pricing(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String operation,
      @Valid @RequestBody RuleInput input) {
    return catalog.saveRule(owner(jwt), operation, input);
  }

  @GetMapping("/admin/wallets/lookup")
  public WalletService.UserWallet lookup(@RequestParam String email) {
    return wallet.lookup(email);
  }

  @GetMapping("/admin/wallets/{id}/reservations")
  public List<Reservation> adminReservations(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
    return wallet.reservations(id, page);
  }

  @GetMapping("/admin/wallets/{id}")
  public Balance adminBalance(@PathVariable UUID id) {
    return wallet.balance(id);
  }

  @PostMapping("/admin/wallets/{id}/adjustments")
  public Entry adjust(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody Adjustment input) {
    return wallet.adjust(id, owner(jwt), input.amount(), input.reason(), input.requestKey());
  }

  @PostMapping("/admin/wallets/{id}/reservations/{reservation}/refunds")
  public Entry refund(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID reservation,
      @Valid @RequestBody Refund input) {
    return wallet.refund(
        id, owner(jwt), reservation, input.amount(), input.reason(), input.requestKey());
  }
}
