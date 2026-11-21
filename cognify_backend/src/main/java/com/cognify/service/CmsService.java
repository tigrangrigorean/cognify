package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.CmsDtos.*;
import com.cognify.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CmsService {
  private final CmsContentRepository content;
  private final CmsBlockRepository blocks;
  private final AuditLogRepository audit;
  private final ObjectMapper mapper;
  private static final Set<String> KEYS =
      Set.of(
          "headline",
          "subtitle",
          "contact",
          "footer",
          "siteName",
          "logoUrl",
          "primaryCta",
          "secondaryCta",
          "ctaButton",
          "toolButton",
          "seoTitle",
          "seoDescription",
          "privacy",
          "terms");

  @Transactional(readOnly = true)
  public Map<String, String> settings() {
    return content.findAll().stream()
        .collect(Collectors.toMap(CmsContent::getContentKey, CmsContent::getValue));
  }

  @Transactional
  public Map<String, String> saveSetting(UUID actor, String key, String value) {
    if (!KEYS.contains(key))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported content key");
    if (key.equals("logoUrl") && !validLogo(value))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Logo must be an HTTPS URL or local path");
    if (Set.of("siteName", "primaryCta", "secondaryCta", "ctaButton", "toolButton", "seoTitle")
            .contains(key)
        && value.length() > 200)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value too long");
    CmsContent c = content.lockKey(key).orElseGet(CmsContent::new);
    String before = c.getValue();
    c.setContentKey(key);
    c.setValue(value);
    content.saveAndFlush(c);
    record(actor, "CMS_SETTING_UPDATED", key, before, value);
    return Map.of("key", key, "value", value);
  }

  @Transactional(readOnly = true)
  public List<BlockView> list(boolean includeHidden) {
    return blocks.findAllByOrderByPositionAscBlockKeyAsc().stream()
        .filter(b -> includeHidden || b.isVisible())
        .map(this::view)
        .toList();
  }

  @Transactional
  public BlockView saveBlock(UUID actor, String key, BlockInput input) {
    if (Set.of("pricing", "cta").contains(key) && !input.items().isEmpty())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "This block does not support items");
    CmsBlock b =
        blocks
            .findByBlockKey(key)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (b.getVersion() != input.version())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Block changed. Reload before saving.");
    String before = json(view(b));
    b.setTitle(input.title());
    b.setBody(input.body());
    b.setItemsJson(json(input.items()));
    b.setPosition(input.position());
    b.setVisible(input.visible());
    blocks.saveAndFlush(b);
    BlockView result = view(b);
    record(actor, "CMS_BLOCK_UPDATED", key, before, json(result));
    return result;
  }

  @Transactional(readOnly = true)
  public List<AuditView> audit(int page) {
    if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return audit.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, 50)).stream()
        .map(
            a ->
                new AuditView(
                    a.getId(),
                    a.getActorId(),
                    a.getAction(),
                    a.getTarget(),
                    a.getBeforeValue(),
                    a.getAfterValue(),
                    a.getCreatedAt()))
        .toList();
  }

  private boolean validLogo(String value) {
    try {
      var uri = java.net.URI.create(value);
      return value.length() <= 1000
          && (("https".equals(uri.getScheme())
                  && uri.getHost() != null
                  && uri.getUserInfo() == null)
              || (!uri.isAbsolute()
                  && uri.getRawAuthority() == null
                  && value.startsWith("/")
                  && !value.startsWith("//")));
    } catch (IllegalArgumentException e) {
      return false;
    }
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

  private BlockView view(CmsBlock b) {
    try {
      return new BlockView(
          b.getBlockKey(),
          b.getTitle(),
          b.getBody(),
          mapper.readValue(b.getItemsJson(), new TypeReference<List<Item>>() {}),
          b.getPosition(),
          b.isVisible(),
          b.getVersion());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored CMS content", e);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
