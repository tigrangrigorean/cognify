package com.cognify.api.controller;

import com.cognify.model.domain.CmsContent;
import com.cognify.model.dto.Requests.CmsInput;
import com.cognify.repository.CmsContentRepository;
import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CmsController {
  private final CmsContentRepository content;

  @GetMapping("/api/public/content")
  public Map<String, String> read() {
    return content.findAll().stream()
        .collect(Collectors.toMap(CmsContent::getContentKey, CmsContent::getValue));
  }

  @PutMapping("/api/admin/content/{key}")
  @Transactional
  public Map<String, String> write(@PathVariable String key, @Valid @RequestBody CmsInput r) {
    if (!Set.of("headline", "subtitle", "contact", "footer").contains(key))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST);
    CmsContent c = content.findByContentKey(key).orElseGet(CmsContent::new);
    c.setContentKey(key);
    c.setValue(r.value());
    content.save(c);
    return Map.of("key", key, "value", r.value());
  }
}
