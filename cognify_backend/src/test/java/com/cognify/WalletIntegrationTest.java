package com.cognify;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cognify.config.PaymentConfig;
import com.cognify.model.dto.CoinDtos.*;
import com.cognify.model.dto.Requests.Register;
import com.cognify.model.vo.*;
import com.cognify.repository.*;
import com.cognify.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "cognify.jwt-secret=wallet-test-secret-at-least-32-characters",
      "cognify.payments.test-enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WalletIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String pg = System.getenv("COGNIFY_POSTGRES_TEST_URL");
    r.add(
        "spring.datasource.url",
        () -> pg == null ? "jdbc:h2:mem:wallet;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : pg);
    r.add(
        "spring.datasource.driver-class-name",
        () -> pg == null ? "org.h2.Driver" : "org.postgresql.Driver");
    r.add(
        "spring.datasource.username",
        () ->
            pg == null
                ? "sa"
                : System.getenv().getOrDefault("COGNIFY_POSTGRES_TEST_USER", "postgres"));
    r.add(
        "spring.datasource.password",
        () -> pg == null ? "" : System.getenv("COGNIFY_POSTGRES_TEST_PASSWORD"));
  }

  @Autowired WalletService wallet;
  @Autowired AuthService auth;
  @Autowired AccountRepository accounts;
  @Autowired CoinCatalogService catalog;
  @Autowired PaymentService payments;
  @Autowired CoinTransactionRepository ledger;
  @Autowired UsageRecordRepository usage;
  @Autowired MockMvc mvc;

  private record User(UUID id, String token) {}

  private User user() {
    String email = UUID.randomUUID() + "@example.com";
    var a = auth.register(new Register(email, "long-password-123", "Test"));
    return new User(accounts.findByEmail(email).orElseThrow().getId(), a.accessToken());
  }

  private void conflict(Runnable call) {
    var e = assertThrows(ResponseStatusException.class, call::run);
    assertEquals(409, e.getStatusCode().value());
  }

  private void reconciles(UUID id) {
    var b = wallet.balance(id);
    var tx = wallet.transactions(id, 0);
    assertEquals(b.balance(), tx.stream().mapToLong(Entry::amount).sum());
    assertEquals(b.reserved(), tx.stream().mapToLong(Entry::reservedDelta).sum());
    assertEquals(b.balance() - b.reserved(), b.available());
  }

  private List<Object> race(Supplier<?> a, Supplier<?> b) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      List<Callable<Object>> work =
          List.of(
              () -> {
                barrier.await();
                try {
                  return a.get();
                } catch (ResponseStatusException e) {
                  return e.getStatusCode().value();
                }
              },
              () -> {
                barrier.await();
                try {
                  return b.get();
                } catch (ResponseStatusException e) {
                  return e.getStatusCode().value();
                }
              });
      List<Object> out = new ArrayList<>();
      for (var f : pool.invokeAll(work)) out.add(f.get(20, TimeUnit.SECONDS));
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void reserveSettleRefundAreIdempotentAndReconcile() {
    UUID id = user().id();
    wallet.adjust(id, id, 100, "Initial test balance", "initial01");
    var r = wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01");
    assertEquals(new Balance(100, 80, 20), wallet.balance(id));
    assertEquals(r.id(), wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01").id());
    conflict(() -> wallet.reserve(id, "DOCUMENT_GENERATION", 81, "reserve01"));
    conflict(() -> wallet.adjust(id, id, -30, "Cannot spend held funds", "adjust002"));
    assertEquals(2, wallet.transactions(id, 0).size());
    wallet.settle(id, r.id(), 50);
    wallet.settle(id, r.id(), 50);
    assertEquals(new Balance(50, 0, 50), wallet.balance(id));
    conflict(() -> wallet.settle(id, r.id(), 49));
    var refund = wallet.refund(id, id, r.id(), 20, "Partial refund", "refund001");
    assertEquals(
        refund.id(), wallet.refund(id, id, r.id(), 20, "Partial refund", "refund001").id());
    conflict(() -> wallet.refund(id, id, r.id(), 31, "Too large", "refund002"));
    wallet.refund(id, id, r.id(), 30, "Remainder", "refund003");
    assertEquals(100, wallet.balance(id).balance());
    assertEquals(1, usage.findAll().stream().filter(u -> u.getOwnerId().equals(id)).count());
    reconciles(id);
  }

  @Test
  void releaseAndOwnerIsolation() {
    UUID id = user().id(), other = user().id();
    wallet.adjust(id, id, 100, "Initial", "initial01");
    var r = wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01");
    var e = assertThrows(ResponseStatusException.class, () -> wallet.release(other, r.id()));
    assertEquals(404, e.getStatusCode().value());
    wallet.release(id, r.id());
    wallet.release(id, r.id());
    assertEquals(new Balance(100, 0, 100), wallet.balance(id));
    conflict(() -> wallet.settle(id, r.id(), 10));
    reconciles(id);
  }

  @Test
  void concurrentReservationsCannotOverspend() throws Exception {
    UUID id = user().id();
    wallet.adjust(id, id, 100, "Initial", "initial01");
    var result =
        race(
            () -> wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01"),
            () -> wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve02"));
    assertEquals(1, result.stream().filter(x -> Integer.valueOf(409).equals(x)).count());
    assertEquals(new Balance(100, 80, 20), wallet.balance(id));
    reconciles(id);
  }

  @Test
  void concurrentAdjustmentAndSettlementAreExactlyOnce() throws Exception {
    UUID id = user().id();
    var credits =
        race(
            () -> wallet.adjust(id, id, 100, "Initial", "initial01"),
            () -> wallet.adjust(id, id, 100, "Initial", "initial01"));
    assertEquals(((Entry) credits.get(0)).id(), ((Entry) credits.get(1)).id());
    assertEquals(100, wallet.balance(id).balance());
    var r = wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01");
    race(() -> wallet.settle(id, r.id(), 60), () -> wallet.settle(id, r.id(), 60));
    assertEquals(40, wallet.balance(id).balance());
    assertEquals(3, wallet.transactions(id, 0).size());
    var refunds =
        race(
            () -> wallet.refund(id, id, r.id(), 40, "A", "refund001"),
            () -> wallet.refund(id, id, r.id(), 40, "B", "refund002"));
    assertEquals(1, refunds.stream().filter(x -> Integer.valueOf(409).equals(x)).count());
    assertEquals(80, wallet.balance(id).balance());
    reconciles(id);
  }

  @Test
  void cancelVersusSettlementHasOnlyOneTerminalState() throws Exception {
    UUID id = user().id();
    wallet.adjust(id, id, 100, "Initial", "initial01");
    var r = wallet.reserve(id, "DOCUMENT_GENERATION", 80, "reserve01");
    var results = race(() -> wallet.settle(id, r.id(), 20), () -> wallet.release(id, r.id()));
    assertEquals(1, results.stream().filter(x -> Integer.valueOf(409).equals(x)).count());
    assertEquals(0, wallet.balance(id).reserved());
    assertTrue(Set.of(80L, 100L).contains(wallet.balance(id).balance()));
    reconciles(id);
  }

  @Test
  void concurrentTestPaymentsCreditOnceAndSnapshotPrice() throws Exception {
    UUID id = user().id();
    var pack = catalog.savePackage(id, null, new PackageInput("Test", 100, 100000, "AMD", true, 0));
    PaymentInput input = new PaymentInput(pack.id(), "payment01");
    var result = race(() -> payments.create(id, input), () -> payments.create(id, input));
    assertEquals(((PaymentView) result.get(0)).id(), ((PaymentView) result.get(1)).id());
    assertEquals(100, wallet.balance(id).balance());
    assertTrue(payments.list(id, 0).get(0).test());
    catalog.savePackage(
        id, pack.id(), new PackageInput("Changed", 200, 200000, "AMD", false, pack.version()));
    assertEquals(100, payments.create(id, input).coins());
    assertEquals(1, payments.list(id, 0).size());
    assertEquals(1, wallet.transactions(id, 0).size());
    reconciles(id);
  }

  @Test
  void quotesAndProviderSafety() {
    UUID id = user().id();
    String op = "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    catalog.saveRule(id, op, new RuleInput(PricingUnit.PAGE, 10, true, 0));
    assertEquals(30, catalog.quote(op, 3).coins());
    var config = new PaymentConfig();
    var prod = new MockEnvironment();
    prod.setActiveProfiles("prod");
    assertThrows(IllegalStateException.class, () -> config.paymentProvider(prod, true));
    assertFalse(config.paymentProvider(prod, false).available());
    assertFalse(config.paymentProvider(prod, false).verifyNotification(new byte[0], "anything"));
    assertThrows(
        ResponseStatusException.class,
        () -> config.paymentProvider(prod, false).create(UUID.randomUUID(), 1, "AMD"));
  }

  @Test
  void userCannotAdjustOrReadAnotherWallet() throws Exception {
    var u = user();
    var other = user();
    wallet.adjust(other.id(), other.id(), 100, "Initial", "initial01");
    String bearer = "Bearer " + u.token();
    mvc.perform(get("/api/wallet").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));
    mvc.perform(get("/api/wallet/transactions").header("Authorization", bearer))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            post("/api/admin/wallets/" + other.id() + "/adjustments")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content("{\"amount\":100,\"reason\":\"No\",\"requestKey\":\"attack001\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/admin/wallets/lookup")
                .param("email", "test@example.com")
                .header("Authorization", bearer))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/wallet/reservations")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }
}
