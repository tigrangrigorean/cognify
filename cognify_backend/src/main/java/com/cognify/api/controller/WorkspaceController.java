package com.cognify.api.controller;

import com.cognify.model.dto.Requests.DocumentInput;
import com.cognify.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {
  private final WorkspaceService service;

  @GetMapping("/me")
  public WorkspaceService.Profile me(@AuthenticationPrincipal Jwt jwt) {
    return service.profile(UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/documents")
  public List<WorkspaceService.Doc> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/documents/{id}")
  public WorkspaceService.Doc get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(UUID.fromString(jwt.getSubject()), id);
  }

  @PostMapping("/documents")
  public WorkspaceService.Doc create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentInput r) {
    return service.save(UUID.fromString(jwt.getSubject()), null, r);
  }

  @PutMapping("/documents/{id}")
  public WorkspaceService.Doc update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody DocumentInput r) {
    return service.save(UUID.fromString(jwt.getSubject()), id, r);
  }

  @DeleteMapping("/documents/{id}")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    service.delete(UUID.fromString(jwt.getSubject()), id);
  }
}
