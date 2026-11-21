package com.cognify.api.controller;

import com.cognify.model.dto.CmsDtos.*;
import com.cognify.model.dto.Requests.CmsInput;
import com.cognify.service.CmsService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CmsController {
  private final CmsService cms;

  @GetMapping("/api/public/content")
  public Map<String, String> read() {
    return cms.settings();
  }

  @GetMapping("/api/public/blocks")
  public List<BlockView> publicBlocks() {
    return cms.list(false);
  }

  @GetMapping("/api/admin/blocks")
  public List<BlockView> adminBlocks() {
    return cms.list(true);
  }

  @PutMapping("/api/admin/blocks/{key}")
  public BlockView block(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String key,
      @Valid @RequestBody BlockInput r) {
    return cms.saveBlock(UUID.fromString(jwt.getSubject()), key, r);
  }

  @PutMapping("/api/admin/content/{key}")
  public Map<String, String> write(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String key, @Valid @RequestBody CmsInput r) {
    return cms.saveSetting(UUID.fromString(jwt.getSubject()), key, r.value());
  }

  @GetMapping("/api/admin/audit")
  public List<AuditView> audit(@RequestParam(defaultValue = "0") int page) {
    return cms.audit(page);
  }
}
