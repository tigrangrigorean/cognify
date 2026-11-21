package com.cognify;

import static org.junit.jupiter.api.Assertions.*;

import com.cognify.config.AiConfig;
import com.cognify.model.dto.CoinDtos.RuleInput;
import com.cognify.model.dto.GenerationDtos.*;
import com.cognify.model.dto.Requests.Register;
import com.cognify.model.vo.PricingUnit;
import com.cognify.repository.*;
import com.cognify.service.*;
import com.cognify.service.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "cognify.jwt-secret=generation-test-secret-at-least-32-characters",
      "cognify.ai.mode=mock",
      "cognify.generation.worker-enabled=false",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@ActiveProfiles("dev")
class GenerationIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String pg = System.getenv("COGNIFY_POSTGRES_TEST_URL");
    r.add(
        "spring.datasource.url",
        () -> pg == null ? "jdbc:h2:mem:generation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : pg);
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

  @Autowired GenerationService generation;
  @Autowired AiProvider ai;
  @Autowired AuthService auth;
  @Autowired AccountRepository accounts;
  @Autowired WalletService wallet;
  @Autowired CoinCatalogService catalog;
  @Autowired GenerationJobRepository jobs;
  @Autowired AiUsageRepository usage;
  @Autowired WorkspaceService workspace;
  @Autowired DocumentSectionService sections;
  @Autowired DocxExportService export;
  UUID owner;

  @BeforeEach
  void setup() {
    String email = UUID.randomUUID() + "@example.com";
    auth.register(new Register(email, "test-password-123", "Test"));
    owner = accounts.findByEmail(email).orElseThrow().getId();
    wallet.adjust(owner, owner, 1000, "Generation test funds", "initial01");
    var rule =
        catalog.rules().stream()
            .filter(r -> r.operation().equals("DOCUMENT_GENERATION"))
            .findFirst()
            .orElseThrow();
    catalog.saveRule(
        owner, "DOCUMENT_GENERATION", new RuleInput(PricingUnit.PAGE, 10, true, rule.version()));
  }

  Start request(String key) {
    return new Start(
        "Հայաստանի տնտեսությունը", "ESSAY", "hy", 3, "Տնտեսագիտություն", "", "", 30, key);
  }

  JobView start() {
    return generation.start(owner, request(UUID.randomUUID().toString()));
  }

  void stage() {
    var c = generation.claim().orElseThrow();
    generation.complete(c, c.stepIndex() < 0 ? ai.plan(c.request()) : ai.section(c.request()));
  }

  JobView plan(JobView j) {
    stage();
    var p = generation.get(owner, j.id());
    assertEquals("AWAITING_PLAN", p.status());
    return generation.approve(owner, p.id(), new Plan(p.plan(), p.version()));
  }

  JobView finish(JobView j) {
    plan(j);
    stage();
    stage();
    stage();
    return generation.get(owner, j.id());
  }

  @Test
  void completeEditExportAndOwnership() throws Exception {
    var j = finish(start());
    assertEquals("COMPLETED", j.status());
    assertEquals(10, j.chargedCoins());
    assertEquals(990, wallet.balance(owner).balance());
    assertEquals(0, wallet.balance(owner).reserved());
    assertEquals(4, usage.findByJobId(j.id()).size());
    var d = workspace.get(owner, j.documentId());
    var parts = sections.list(owner, d.id());
    assertEquals(3, parts.size());
    var updated =
        sections.save(
            owner,
            d.id(),
            parts.get(1).id(),
            new SectionInput("Խմբագրված", "Հայերեն նոր բովանդակություն", d.version()));
    assertEquals(parts.get(0), sections.list(owner, d.id()).get(0));
    assertThrows(
        ResponseStatusException.class,
        () ->
            sections.save(
                owner, d.id(), parts.get(1).id(), new SectionInput("Stale", "lost", d.version())));
    assertEquals(d.content(), sections.history(owner, d.id()).get(0).content());
    var ids = new ArrayList<>(parts.stream().map(SectionView::id).toList());
    Collections.reverse(ids);
    updated = sections.reorder(owner, d.id(), new Order(ids, updated.version()));
    assertEquals(ids, sections.list(owner, d.id()).stream().map(SectionView::id).toList());
    try (var doc = new XWPFDocument(new ByteArrayInputStream(export.export(owner, d.id())))) {
      String text =
          doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
      assertTrue(text.contains("Հայերեն նոր բովանդակություն"));
      assertTrue(text.contains("ԹԵՍՏԱՅԻՆ ՓԱՍՏԱԹՈՒՂԹ"));
      assertEquals("11906", doc.getDocument().getBody().getSectPr().getPgSz().getW().toString());
    }
    UUID other = UUID.randomUUID();
    assertThrows(ResponseStatusException.class, () -> export.export(other, d.id()));
    assertThrows(ResponseStatusException.class, () -> generation.get(other, j.id()));
    sections.delete(owner, d.id(), ids.get(0), updated.version());
    assertEquals(2, sections.list(owner, d.id()).size());
  }

  @Test
  void idempotencyAndQuoteProtection() {
    var r = request("same-request");
    var j = generation.start(owner, r);
    assertEquals(j.id(), generation.start(owner, r).id());
    assertEquals(30, wallet.balance(owner).reserved());
    assertThrows(
        ResponseStatusException.class,
        () ->
            generation.start(
                owner, new Start("Changed", "ESSAY", "hy", 3, "", "", "", 30, "same-request")));
    assertThrows(
        ResponseStatusException.class,
        () ->
            generation.start(
                owner, new Start("Price", "ESSAY", "hy", 3, "", "", "", 20, "wrong-price")));
    generation.control(owner, j.id(), "cancel");
    assertEquals(1000, wallet.balance(owner).available());
  }

  @Test
  void pausedAndCancelledInflightResultsAreDiscarded() {
    var j = start();
    var c = generation.claim().orElseThrow();
    generation.control(owner, j.id(), "pause");
    generation.complete(c, ai.plan(c.request()));
    assertEquals("PAUSED", generation.get(owner, j.id()).status());
    assertEquals(0, usage.findByJobId(j.id()).size());
    generation.control(owner, j.id(), "resume");
    var next = generation.claim().orElseThrow();
    generation.control(owner, j.id(), "cancel");
    generation.complete(next, ai.plan(next.request()));
    generation.control(owner, j.id(), "cancel");
    assertEquals(1000, wallet.balance(owner).available());
  }

  @Test
  void failureRetryReusesCompletedSectionsAndSettlesOnce() {
    var j = plan(start());
    stage();
    var failed = generation.claim().orElseThrow();
    generation.fail(failed, "PROVIDER_TIMEOUT");
    assertEquals(0, wallet.balance(owner).reserved());
    assertEquals(1, generation.get(owner, j.id()).completedSections());
    generation.control(owner, j.id(), "retry");
    generation.control(owner, j.id(), "retry");
    assertEquals(30, wallet.balance(owner).reserved());
    var retried = generation.claim().orElseThrow();
    assertEquals(failed.stepIndex(), retried.stepIndex());
    generation.complete(retried, ai.section(retried.request()));
    generation.complete(retried, ai.section(retried.request()));
    stage();
    assertEquals("COMPLETED", generation.get(owner, j.id()).status());
    assertEquals(990, wallet.balance(owner).balance());
    assertEquals(4, usage.findByJobId(j.id()).size());
  }

  @Test
  void expiredLeaseFencesPreviousWorker() {
    var j = start();
    var old = generation.claim().orElseThrow();
    var entity = jobs.findById(j.id()).orElseThrow();
    entity.setLeaseUntil(Instant.now().minusSeconds(1));
    jobs.saveAndFlush(entity);
    var replacement = generation.claim().orElseThrow();
    assertNotEquals(old.leaseToken(), replacement.leaseToken());
    generation.complete(old, ai.plan(old.request()));
    assertEquals("RUNNING", generation.get(owner, j.id()).status());
    generation.complete(replacement, ai.plan(replacement.request()));
    assertEquals(1, usage.findByJobId(j.id()).size());
    generation.control(owner, j.id(), "cancel");
  }

  @Test
  void mockForbiddenInProductionAndMissingKeyIsUnavailable() {
    var config = new AiConfig();
    var env = new MockEnvironment();
    env.setActiveProfiles("dev", "prod");
    assertThrows(
        IllegalStateException.class,
        () ->
            config.aiProvider(
                env, new ObjectMapper(), "mock", "", "model", "https://api.openai.com/v1"));
    assertFalse(
        config
            .aiProvider(env, new ObjectMapper(), "openai", "", "model", "https://api.openai.com/v1")
            .available());
  }
}
