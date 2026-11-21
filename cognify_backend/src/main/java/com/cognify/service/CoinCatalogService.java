package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.CoinDtos.*;
import com.cognify.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CoinCatalogService {
  private final CoinPackageRepository packages;
  private final PricingRuleRepository rules;
  private final AuditLogRepository audit;

  @Transactional(readOnly = true)
  public List<PackageView> packages(boolean admin) {
    return packages.findAllByOrderByCoinsAsc().stream()
        .filter(p -> admin || p.isActive())
        .map(this::view)
        .toList();
  }

  @Transactional
  public PackageView savePackage(UUID actor, UUID id, PackageInput input) {
    CoinPackage p =
        id == null
            ? new CoinPackage()
            : packages
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (id != null && p.getVersion() != input.version())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Package changed; reload");
    String before = id == null ? null : view(p).toString();
    p.setName(input.name());
    p.setCoins(input.coins());
    p.setAmountMinor(input.amountMinor());
    p.setCurrency(input.currency());
    p.setActive(input.active());
    packages.saveAndFlush(p);
    record(actor, "COIN_PACKAGE_UPDATED", p.getId().toString(), before, view(p).toString());
    return view(p);
  }

  @Transactional(readOnly = true)
  public List<RuleView> rules() {
    return rules.findAllByOrderByOperationAsc().stream().map(this::view).toList();
  }

  @Transactional
  public RuleView saveRule(UUID actor, String operation, RuleInput input) {
    if (!operation.matches("[A-Z][A-Z0-9_]{1,79}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    PricingRule p = rules.findByOperation(operation).orElseGet(PricingRule::new);
    if (p.getId() != null && p.getVersion() != input.version())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Rule changed; reload");
    String before = p.getId() == null ? null : view(p).toString();
    p.setOperation(operation);
    p.setUnit(input.unit());
    p.setUnitCost(input.unitCost());
    p.setActive(input.active());
    rules.saveAndFlush(p);
    record(actor, "PRICING_RULE_UPDATED", operation, before, view(p).toString());
    return view(p);
  }

  @Transactional(readOnly = true)
  public Quote quote(String operation, long units) {
    if (units <= 0 || units > 100000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid units");
    PricingRule p =
        rules
            .findByOperation(operation)
            .filter(PricingRule::isActive)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Pricing is not configured"));
    long coins = p.getUnitCost() * units;
    if (coins > 1000000000L)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quote exceeds operation limit");
    return new Quote(operation, p.getUnit(), units, coins);
  }

  private PackageView view(CoinPackage p) {
    return new PackageView(
        p.getId(),
        p.getName(),
        p.getCoins(),
        p.getAmountMinor(),
        p.getCurrency(),
        p.isActive(),
        p.getVersion());
  }

  private RuleView view(PricingRule p) {
    return new RuleView(
        p.getOperation(), p.getUnit(), p.getUnitCost(), p.isActive(), p.getVersion());
  }

  private void record(UUID actor, String action, String target, String before, String after) {
    AuditLog a = new AuditLog();
    a.setActorId(actor);
    a.setAction(action);
    a.setTarget(target);
    a.setBeforeValue(before);
    a.setAfterValue(after);
    audit.save(a);
  }
}
