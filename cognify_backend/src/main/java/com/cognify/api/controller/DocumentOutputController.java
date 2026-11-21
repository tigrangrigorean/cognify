package com.cognify.api.controller;

import com.cognify.model.dto.GenerationDtos.*;
import com.cognify.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{id}")
@RequiredArgsConstructor
public class DocumentOutputController {
  private final DocumentSectionService sections;
  private final DocxExportService export;

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  @GetMapping("/sections")
  public List<SectionView> sections(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return sections.list(owner(jwt), id);
  }

  @GetMapping("/versions")
  public List<VersionView> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return sections.history(owner(jwt), id);
  }

  @PostMapping("/sections")
  public WorkspaceService.Doc add(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody SectionInput input) {
    return sections.save(owner(jwt), id, null, input);
  }

  @PutMapping("/sections/{section}")
  public WorkspaceService.Doc edit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID section,
      @Valid @RequestBody SectionInput input) {
    return sections.save(owner(jwt), id, section, input);
  }

  @DeleteMapping("/sections/{section}")
  public WorkspaceService.Doc delete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID section,
      @RequestParam long version) {
    return sections.delete(owner(jwt), id, section, version);
  }

  @PutMapping("/section-order")
  public WorkspaceService.Doc order(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Order input) {
    return sections.reorder(owner(jwt), id, input);
  }

  @GetMapping("/export.docx")
  public ResponseEntity<byte[]> docx(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cognify-" + id + ".docx\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .body(export.export(owner(jwt), id));
  }
}
