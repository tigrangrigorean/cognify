package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.GenerationDtos.*;
import com.cognify.model.vo.PricingUnit;
import com.cognify.repository.*;
import com.cognify.service.ai.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GenerationService {
  private final GenerationJobRepository jobs;
  private final GenerationStepRepository steps;
  private final AiUsageRepository aiUsage;
  private final DocumentRepository documents;
  private final DocumentSectionRepository sections;
  private final WalletRepository wallets;
  private final WalletService coins;
  private final CoinCatalogService catalog;
  private final AiProvider provider;
  private final ObjectMapper mapper;

  public record Claim(UUID jobId, UUID leaseToken, int stepIndex, AiProvider.Request request) {}

  public Config config() {
    return new Config(provider.available(), provider.mock(), provider.name(), provider.model(), 40);
  }

  public Quote quote(int pages) {
    if (pages < 1 || pages > 40) throw error(400, "Invalid page count");
    var q = catalog.quote("DOCUMENT_GENERATION", pages);
    if (q.unit() != PricingUnit.PAGE) throw error(503, "Generation pricing must use PAGE units");
    return new Quote(
        pages,
        q.coins(),
        "PAGE",
        "350 words per billable page; final charge cannot exceed this estimate");
  }

  @Transactional
  public JobView start(UUID owner, Start r) {
    wallets.lockOwner(owner).orElseThrow(() -> error(404, "Wallet not found"));
    String fp = hash(json(r));
    var old = jobs.findByOwnerIdAndRequestKey(owner, r.requestKey());
    if (old.isPresent()) {
      if (!old.get().getFingerprint().equals(fp)) throw error(409, "Request key conflict");
      return view(old.get());
    }
    requireProvider();
    if (jobs.countByOwnerIdAndStatusIn(
            owner, List.of("QUEUED", "RUNNING", "AWAITING_PLAN", "PAUSED"))
        >= 2) throw error(409, "Finish or cancel existing generations first");
    Quote q = quote(r.pages());
    if (q.coins() != r.acceptedQuote())
      throw error(409, "Price changed. Refresh the quote before starting");
    GenerationJob j = new GenerationJob();
    j.setOwnerId(owner);
    j.setRequestKey(r.requestKey());
    j.setFingerprint(fp);
    j.setTopic(r.topic());
    j.setKind(r.kind());
    j.setLanguage(r.language());
    j.setSubject(r.subject());
    j.setUniversity(r.university());
    j.setRequirements(r.requirements());
    j.setPages(r.pages());
    j.setProvider(provider.name());
    j.setModel(provider.model());
    j.setQuotedCoins(q.coins());
    j.setUnitCost(q.coins() / r.pages());
    jobs.saveAndFlush(j);
    j.setReservationId(coins.reserve(owner, "DOCUMENT_GENERATION", q.coins(), billingKey(j)).id());
    touch(j);
    return view(j);
  }

  @Transactional(readOnly = true)
  public JobView get(UUID owner, UUID id) {
    return view(
        jobs.findByIdAndOwnerId(id, owner).orElseThrow(() -> error(404, "Generation not found")));
  }

  @Transactional(readOnly = true)
  public List<JobView> list(UUID owner) {
    return jobs.findByOwnerIdOrderByCreatedAtDesc(owner, PageRequest.of(0, 30)).stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public JobView approve(UUID owner, UUID id, Plan input) {
    GenerationJob j = ownedLock(owner, id);
    List<String> plan = validatePlan(input.headings());
    if (j.getPhase().equals("SECTIONS")) {
      if (!readPlan(j).equals(plan)) throw error(409, "Plan is already approved");
      return view(j);
    }
    if (!j.getStatus().equals("AWAITING_PLAN") || j.getVersion() != input.version())
      throw error(409, "Generation changed. Reload before approving");
    j.setPlanJson(json(plan));
    j.setPhase("SECTIONS");
    for (int i = 0; i < plan.size(); i++) {
      GenerationStep s = new GenerationStep();
      s.setJobId(id);
      s.setPosition(i);
      s.setTitle(plan.get(i));
      steps.save(s);
    }
    j.setStatus("QUEUED");
    touch(j);
    return view(j);
  }

  @Transactional
  public JobView control(UUID owner, UUID id, String action) {
    GenerationJob j = ownedLock(owner, id);
    switch (action) {
      case "pause" -> {
        if (j.getStatus().equals("PAUSED")) return view(j);
        if (!Set.of("QUEUED", "RUNNING").contains(j.getStatus()))
          throw error(409, "Cannot pause this generation");
        j.setStatus("PAUSED");
        clearLease(j);
      }
      case "resume" -> {
        if (Set.of("QUEUED", "RUNNING").contains(j.getStatus())) return view(j);
        if (!j.getStatus().equals("PAUSED")) throw error(409, "Cannot resume this generation");
        requireCompatible(j);
        j.setStatus("QUEUED");
      }
      case "cancel" -> {
        if (j.getStatus().equals("CANCELLED")) return view(j);
        if (j.getStatus().equals("COMPLETED"))
          throw error(409, "Completed generation cannot be cancelled");
        coins.release(owner, j.getReservationId());
        j.setStatus("CANCELLED");
        clearLease(j);
      }
      case "retry" -> {
        if (Set.of("QUEUED", "RUNNING").contains(j.getStatus())) return view(j);
        if (!j.getStatus().equals("FAILED")) throw error(409, "Only failed generation can retry");
        requireCompatible(j);
        if (j.getPhase().equals("PLAN")
            ? j.getPlanAttempts() >= 3
            : steps.findByJobIdOrderByPositionAsc(id).stream()
                .anyMatch(s -> !s.isCompleted() && s.getAttempts() >= 3))
          throw error(409, "Retry limit reached; create a new generation");
        j.setBillingAttempt(j.getBillingAttempt() + 1);
        j.setReservationId(
            coins.reserve(owner, "DOCUMENT_GENERATION", j.getQuotedCoins(), billingKey(j)).id());
        j.setErrorCode(null);
        j.setStatus("QUEUED");
      }
      default -> throw error(400, "Unknown action");
    }
    touch(j);
    return view(j);
  }

  /** A short DB transaction claims one stage. No network call occurs under this lock. */
  @Transactional
  public Optional<Claim> claim() {
    var candidates = jobs.claimable(Instant.now(), PageRequest.of(0, 1));
    if (candidates.isEmpty()) return Optional.empty();
    GenerationJob j = candidates.get(0);
    if (!provider.available()
        || !provider.name().equals(j.getProvider())
        || !provider.model().equals(j.getModel())) {
      failLocked(j, "PROVIDER_UNAVAILABLE");
      return Optional.empty();
    }
    List<GenerationStep> all = steps.findByJobIdOrderByPositionAsc(j.getId());
    int index = -1;
    String title = "", previous = "";
    if (j.getPhase().equals("PLAN")) {
      if (j.getPlanAttempts() >= 3) {
        failLocked(j, "RETRY_LIMIT");
        return Optional.empty();
      }
      j.setPlanAttempts(j.getPlanAttempts() + 1);
    } else {
      var next = all.stream().filter(s -> !s.isCompleted()).findFirst();
      if (next.isEmpty()) {
        failLocked(j, "INVALID_STATE");
        return Optional.empty();
      }
      var s = next.get();
      if (s.getAttempts() >= 3) {
        failLocked(j, "RETRY_LIMIT");
        return Optional.empty();
      }
      s.setAttempts(s.getAttempts() + 1);
      index = s.getPosition();
      title = s.getTitle();
      StringBuilder context = new StringBuilder();
      for (var done : all)
        if (done.isCompleted()) {
          context.append(done.getTitle()).append("\n");
          String t = done.getContent();
          context.append(t.substring(0, Math.min(1000, t.length()))).append("\n");
        }
      previous = context.toString();
    }
    UUID lease = UUID.randomUUID();
    j.setLeaseToken(lease);
    j.setLeaseUntil(Instant.now().plusSeconds(180));
    j.setStatus("RUNNING");
    touch(j);
    List<String> plan = readPlan(j);
    return Optional.of(
        new Claim(
            j.getId(),
            lease,
            index,
            new AiProvider.Request(
                j.getTopic(),
                j.getKind(),
                j.getLanguage(),
                j.getPages(),
                j.getSubject(),
                j.getUniversity(),
                j.getRequirements(),
                plan,
                title,
                previous,
                Math.max(120, j.getPages() * 350 / Math.max(3, plan.size())))));
  }

  @Transactional
  public void complete(Claim claim, AiProvider.Result result) {
    GenerationJob j = jobs.lock(claim.jobId()).orElseThrow();
    if (!ownsLease(j, claim)) return;
    if (result.text() == null || result.text().isBlank() || result.text().length() > 16000)
      throw new AiFailure("INVALID_OUTPUT");
    if (claim.stepIndex() < 0) {
      List<String> plan;
      try {
        plan =
            validatePlan(
                mapper.convertValue(
                    mapper.readTree(result.text()).get("headings"),
                    new TypeReference<List<String>>() {}));
      } catch (Exception e) {
        throw new AiFailure("INVALID_PLAN");
      }
      j.setPlanJson(json(plan));
      j.setStatus("AWAITING_PLAN");
    } else {
      List<GenerationStep> all = steps.findByJobIdOrderByPositionAsc(j.getId());
      GenerationStep s =
          all.stream().filter(x -> x.getPosition() == claim.stepIndex()).findFirst().orElseThrow();
      s.setContent(result.text());
      s.setCompleted(true);
      if (all.stream().allMatch(GenerationStep::isCompleted)) {
        finish(j, all);
      } else j.setStatus("QUEUED");
    }
    AiUsage u = new AiUsage();
    u.setJobId(j.getId());
    u.setStepIndex(claim.stepIndex());
    u.setProvider(j.getProvider());
    u.setModel(j.getModel());
    u.setInputTokens(result.inputTokens());
    u.setOutputTokens(result.outputTokens());
    aiUsage.save(u);
    clearLease(j);
    touch(j);
  }

  @Transactional
  public void fail(Claim claim, String code) {
    GenerationJob j = jobs.lock(claim.jobId()).orElseThrow();
    if (ownsLease(j, claim)) failLocked(j, code);
  }

  private void finish(GenerationJob j, List<GenerationStep> all) {
    Document d = new Document();
    d.setOwnerId(j.getOwnerId());
    d.setTitle(j.getTopic());
    d.setKind(j.getKind());
    d.setGenerationMode(j.getProvider());
    String full =
        all.stream()
            .map(s -> s.getTitle() + "\n\n" + s.getContent())
            .reduce((a, b) -> a + "\n\n" + b)
            .orElse("");
    if (full.length() > 200000) throw new AiFailure("DOCUMENT_TOO_LARGE");
    d.setContent(full);
    documents.saveAndFlush(d);
    for (var step : all) {
      DocumentSection s = new DocumentSection();
      s.setDocumentId(d.getId());
      s.setPosition(step.getPosition());
      s.setTitle(step.getTitle());
      s.setContent(step.getContent());
      sections.save(s);
    }
    long words = all.stream().mapToLong(s -> countWords(s.getContent())).sum();
    long billable = Math.min(j.getPages(), Math.max(1, (words + 349) / 350));
    long charge = billable * j.getUnitCost();
    coins.settle(j.getOwnerId(), j.getReservationId(), charge);
    j.setChargedCoins(charge);
    j.setDocumentId(d.getId());
    j.setStatus("COMPLETED");
  }

  private long countWords(String text) {
    return text.isBlank() ? 0 : text.trim().split("\\s+").length;
  }

  private boolean ownsLease(GenerationJob j, Claim c) {
    return j.getStatus().equals("RUNNING") && Objects.equals(j.getLeaseToken(), c.leaseToken());
  }

  private void failLocked(GenerationJob j, String code) {
    coins.release(j.getOwnerId(), j.getReservationId());
    j.setStatus("FAILED");
    j.setErrorCode(code);
    clearLease(j);
    touch(j);
  }

  private GenerationJob ownedLock(UUID owner, UUID id) {
    GenerationJob j = jobs.lock(id).orElseThrow(() -> error(404, "Generation not found"));
    if (!j.getOwnerId().equals(owner)) throw error(404, "Generation not found");
    return j;
  }

  private void requireProvider() {
    if (!provider.available()) throw error(503, "AI generation is not configured");
  }

  private void requireCompatible(GenerationJob j) {
    requireProvider();
    if (!j.getProvider().equals(provider.name()) || !j.getModel().equals(provider.model()))
      throw error(409, "AI provider changed; create a new generation");
  }

  private void clearLease(GenerationJob j) {
    j.setLeaseToken(null);
    j.setLeaseUntil(null);
  }

  private void touch(GenerationJob j) {
    j.setUpdatedAt(Instant.now());
    jobs.saveAndFlush(j);
  }

  private String billingKey(GenerationJob j) {
    return "generation-" + j.getId() + "-" + j.getBillingAttempt();
  }

  private List<String> validatePlan(List<String> input) {
    if (input == null
        || input.size() < 3
        || input.size() > 12
        || input.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 200))
      throw error(400, "Plan must have 3 to 12 headings");
    List<String> clean = input.stream().map(String::trim).toList();
    if (clean.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != clean.size())
      throw error(400, "Duplicate headings");
    return clean;
  }

  private List<String> readPlan(GenerationJob j) {
    try {
      return mapper.readValue(j.getPlanJson(), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JobView view(GenerationJob j) {
    var all = steps.findByJobIdOrderByPositionAsc(j.getId());
    return new JobView(
        j.getId(),
        j.getTopic(),
        j.getKind(),
        j.getLanguage(),
        j.getPages(),
        j.getStatus(),
        j.getPhase(),
        readPlan(j),
        (int) all.stream().filter(GenerationStep::isCompleted).count(),
        all.size(),
        j.getQuotedCoins(),
        j.getChargedCoins(),
        j.getProvider(),
        j.getModel(),
        j.getErrorCode(),
        j.getDocumentId(),
        j.getVersion(),
        j.getCreatedAt());
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ResponseStatusException error(int status, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(status), message);
  }
}
