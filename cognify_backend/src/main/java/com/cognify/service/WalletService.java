package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.CoinDtos.*;
import com.cognify.model.vo.ReservationStatus;
import com.cognify.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WalletService {
  private final WalletRepository wallets;
  private final AccountRepository accounts;
  private final CoinTransactionRepository ledger;
  private final CoinReservationRepository reservations;
  private final UsageRecordRepository usage;
  private final AuditLogRepository audit;
  private final ObjectMapper mapper;
  private static final long MAX_BALANCE = 9007199254740991L;

  public record UserWallet(UUID id, String name, String email, Balance wallet) {}

  @Transactional(readOnly = true)
  public UserWallet lookup(String email) {
    if (email.length() > 254) throw error(HttpStatus.BAD_REQUEST, "Invalid email");
    var account =
        accounts
            .findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
    return new UserWallet(
        account.getId(), account.getName(), account.getEmail(), balance(account.getId()));
  }

  @Transactional(readOnly = true)
  public Balance balance(UUID owner) {
    return balanceOf(
        wallets
            .findByOwnerId(owner)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Wallet not found")));
  }

  @Transactional(readOnly = true)
  public List<Entry> transactions(UUID owner, int page) {
    return ledger.findByOwnerIdOrderByCreatedAtDescIdDesc(owner, page(page)).stream()
        .map(this::entry)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Reservation> reservations(UUID owner, int page) {
    return reservations.findByOwnerIdOrderByCreatedAtDesc(owner, page(page)).stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public Entry adjust(UUID owner, UUID actor, long amount, String reason, String key) {
    validateKey(key);
    validateReason(reason);
    if (amount == 0 || amount > 1000000000L || amount < -1000000000L)
      throw error(HttpStatus.BAD_REQUEST, "Invalid amount");
    Wallet w = lock(owner);
    String request = "adjust:" + key, fp = fingerprint(actor, amount, reason);
    var old = ledger.findByOwnerIdAndRequestKey(owner, request);
    if (old.isPresent()) return replay(old.get(), fp);
    return move(w, actor, "ADJUSTMENT", amount, 0, null, reason, request, fp);
  }

  /** Trusted server-side job API: never accept a client-supplied price. */
  @Transactional
  public Reservation reserve(UUID owner, String operation, long amount, String key) {
    validateKey(key);
    if (operation == null
        || !operation.matches("[A-Z][A-Z0-9_]{1,79}")
        || amount <= 0
        || amount > 1000000000L) throw error(HttpStatus.BAD_REQUEST, "Invalid reservation");
    Wallet w = lock(owner);
    var old = reservations.findByOwnerIdAndRequestKey(owner, key);
    if (old.isPresent()) {
      if (old.get().getAmount() != amount || !old.get().getOperation().equals(operation))
        throw conflict();
      return view(old.get());
    }
    if (w.getBalance() - w.getReserved() < amount)
      throw error(HttpStatus.CONFLICT, "Insufficient Coins");
    CoinReservation r = new CoinReservation();
    r.setOwnerId(owner);
    r.setOperation(operation);
    r.setAmount(amount);
    r.setRequestKey(key);
    reservations.saveAndFlush(r);
    move(
        w,
        owner,
        "RESERVE",
        0,
        amount,
        r.getId(),
        operation,
        "reserve:" + key,
        fingerprint(operation, amount));
    return view(r);
  }

  @Transactional
  public Reservation settle(UUID owner, UUID id, long actualCost) {
    Wallet w = lock(owner);
    CoinReservation r = find(owner, id);
    if (actualCost < 0 || actualCost > r.getAmount())
      throw error(HttpStatus.BAD_REQUEST, "Actual cost exceeds reservation");
    if (r.getStatus() == ReservationStatus.SETTLED) {
      if (r.getSettledAmount() != actualCost) throw conflict();
      return view(r);
    }
    if (r.getStatus() != ReservationStatus.HELD) throw conflict();
    r.setStatus(ReservationStatus.SETTLED);
    r.setSettledAmount(actualCost);
    move(
        w,
        owner,
        "SETTLE",
        -actualCost,
        -r.getAmount(),
        id,
        r.getOperation(),
        "settle:" + id,
        fingerprint(id, actualCost));
    UsageRecord u = new UsageRecord();
    u.setOwnerId(owner);
    u.setReservationId(id);
    u.setOperation(r.getOperation());
    u.setCoins(actualCost);
    usage.save(u);
    return view(r);
  }

  @Transactional
  public Reservation release(UUID owner, UUID id) {
    Wallet w = lock(owner);
    CoinReservation r = find(owner, id);
    if (r.getStatus() == ReservationStatus.RELEASED) return view(r);
    if (r.getStatus() != ReservationStatus.HELD) throw conflict();
    r.setStatus(ReservationStatus.RELEASED);
    move(
        w,
        owner,
        "RELEASE",
        0,
        -r.getAmount(),
        id,
        r.getOperation(),
        "release:" + id,
        fingerprint(id));
    return view(r);
  }

  @Transactional
  public Entry refund(
      UUID owner, UUID actor, UUID reservationId, long amount, String reason, String key) {
    validateKey(key);
    validateReason(reason);
    if (amount <= 0 || amount > 1000000000L) throw error(HttpStatus.BAD_REQUEST, "Invalid refund");
    Wallet w = lock(owner);
    String request = "refund:" + key, fp = fingerprint(actor, reservationId, amount, reason);
    var old = ledger.findByOwnerIdAndRequestKey(owner, request);
    if (old.isPresent()) return replay(old.get(), fp);
    CoinReservation r = find(owner, reservationId);
    if (r.getStatus() != ReservationStatus.SETTLED
        || amount > r.getSettledAmount() - r.getRefundedAmount())
      throw error(HttpStatus.CONFLICT, "Refund exceeds settled cost");
    r.setRefundedAmount(r.getRefundedAmount() + amount);
    return move(w, actor, "REFUND", amount, 0, reservationId, reason, request, fp);
  }

  /** Called only after the server-side provider has verified success. */
  @Transactional
  public Entry creditPayment(UUID owner, UUID paymentId, long coins, boolean test) {
    if (coins <= 0 || coins > 1000000000L)
      throw error(HttpStatus.BAD_REQUEST, "Invalid payment amount");
    Wallet w = lock(owner);
    String request = "payment:" + paymentId, fp = fingerprint(paymentId, coins, test);
    var old = ledger.findByOwnerIdAndRequestKey(owner, request);
    if (old.isPresent()) return replay(old.get(), fp);
    return move(
        w,
        owner,
        test ? "TEST_PAYMENT" : "PAYMENT",
        coins,
        0,
        paymentId,
        test ? "Թեստային համալրում․ իրական վճարում չկա" : "Հաստատված վճարում",
        request,
        fp);
  }

  private Entry move(
      Wallet w,
      UUID actor,
      String kind,
      long amount,
      long reservedDelta,
      UUID reference,
      String reason,
      String request,
      String fp) {
    long before = w.getBalance(), held = w.getReserved();
    long after = before + amount, reserved = held + reservedDelta;
    if (after < 0 || after > MAX_BALANCE || reserved < 0 || reserved > after)
      throw error(HttpStatus.CONFLICT, "Insufficient available Coins or wallet limit exceeded");
    w.setBalance(after);
    w.setReserved(reserved);
    wallets.saveAndFlush(w);
    CoinTransaction t = new CoinTransaction();
    t.setOwnerId(w.getOwnerId());
    t.setKind(kind);
    t.setAmount(amount);
    t.setReservedDelta(reservedDelta);
    t.setBalanceAfter(after);
    t.setReservedAfter(reserved);
    t.setReferenceId(reference);
    t.setReason(reason);
    t.setRequestKey(request);
    t.setFingerprint(fp);
    ledger.saveAndFlush(t);
    AuditLog a = new AuditLog();
    a.setActorId(actor);
    a.setAction("COINS_" + kind);
    a.setTarget(w.getOwnerId().toString());
    a.setBeforeValue("balance=" + before + ", reserved=" + held);
    a.setAfterValue(
        "balance="
            + after
            + ", reserved="
            + reserved
            + ", transaction="
            + t.getId()
            + ", reason="
            + reason);
    audit.save(a);
    return entry(t);
  }

  private Wallet lock(UUID owner) {
    return wallets
        .lockOwner(owner)
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Wallet not found"));
  }

  private CoinReservation find(UUID owner, UUID id) {
    return reservations
        .findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reservation not found"));
  }

  private Entry replay(CoinTransaction t, String fp) {
    if (!t.getFingerprint().equals(fp)) throw conflict();
    return entry(t);
  }

  private Entry entry(CoinTransaction t) {
    return new Entry(
        t.getId(),
        t.getKind(),
        t.getAmount(),
        t.getReservedDelta(),
        t.getBalanceAfter(),
        t.getReservedAfter(),
        t.getReferenceId(),
        t.getReason(),
        t.getCreatedAt());
  }

  private Reservation view(CoinReservation r) {
    return new Reservation(
        r.getId(),
        r.getOperation(),
        r.getAmount(),
        r.getSettledAmount(),
        r.getRefundedAmount(),
        r.getStatus(),
        r.getCreatedAt());
  }

  private Balance balanceOf(Wallet w) {
    return new Balance(w.getBalance(), w.getReserved(), w.getBalance() - w.getReserved());
  }

  private PageRequest page(int page) {
    if (page < 0 || page > 10000) throw error(HttpStatus.BAD_REQUEST, "Invalid page");
    return PageRequest.of(page, 30);
  }

  private void validateKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,80}"))
      throw error(HttpStatus.BAD_REQUEST, "Invalid idempotency key");
  }

  private void validateReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw error(HttpStatus.BAD_REQUEST, "Reason is required");
  }

  private String fingerprint(Object... values) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(values).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ResponseStatusException conflict() {
    return error(HttpStatus.CONFLICT, "Idempotency key or reservation state conflict");
  }

  private ResponseStatusException error(HttpStatus status, String text) {
    return new ResponseStatusException(status, text);
  }
}
