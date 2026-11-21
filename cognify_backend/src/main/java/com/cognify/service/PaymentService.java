package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.CoinDtos.*;
import com.cognify.repository.*;
import com.cognify.service.payment.PaymentProvider;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentProvider provider;
  private final WalletRepository wallets;
  private final CoinPackageRepository packages;
  private final PaymentRecordRepository payments;
  private final WalletService coins;

  public PaymentMode mode() {
    return new PaymentMode(
        provider.available(),
        provider.testOnly(),
        provider.available()
            ? "Test payments only — no real money"
            : "Payments are not configured");
  }

  @Transactional
  public PaymentView create(UUID owner, PaymentInput input) {
    if (!provider.available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Payments are not configured");
    wallets.lockOwner(owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var old = payments.findByOwnerIdAndRequestKey(owner, input.requestKey());
    if (old.isPresent()) {
      if (!old.get().getPackageId().equals(input.packageId()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key conflict");
      return view(old.get());
    }
    CoinPackage pack =
        packages
            .findById(input.packageId())
            .filter(CoinPackage::isActive)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package unavailable"));
    PaymentRecord p = new PaymentRecord();
    p.setOwnerId(owner);
    p.setPackageId(pack.getId());
    p.setRequestKey(input.requestKey());
    p.setCoins(pack.getCoins());
    p.setAmountMinor(pack.getAmountMinor());
    p.setCurrency(pack.getCurrency());
    p.setProvider(provider.name());
    p.setTest(provider.testOnly());
    p.setStatus("PENDING");
    payments.saveAndFlush(p);
    var receipt = provider.create(p.getId(), p.getAmountMinor(), p.getCurrency());
    p.setProviderReference(receipt.reference());
    if (receipt.confirmed()) {
      p.setStatus("SUCCEEDED");
      coins.creditPayment(owner, p.getId(), p.getCoins(), p.isTest());
    }
    return view(p);
  }

  @Transactional(readOnly = true)
  public List<PaymentView> list(UUID owner, int page) {
    if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return payments.findByOwnerIdOrderByCreatedAtDesc(owner, PageRequest.of(page, 30)).stream()
        .map(this::view)
        .toList();
  }

  private PaymentView view(PaymentRecord p) {
    return new PaymentView(
        p.getId(),
        p.getPackageId(),
        p.getCoins(),
        p.getAmountMinor(),
        p.getCurrency(),
        p.getStatus(),
        p.isTest(),
        p.getCreatedAt());
  }
}
