package com.cognify.api.controller;

import com.cognify.model.dto.GenerationDtos.*;
import com.cognify.service.GenerationService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/generation")
@RequiredArgsConstructor
public class GenerationController {
  private final GenerationService jobs;

  @GetMapping("/config")
  public Config config() {
    return jobs.config();
  }

  @GetMapping("/quote")
  public Quote quote(@RequestParam int pages) {
    return jobs.quote(pages);
  }

  @GetMapping("/jobs")
  public List<JobView> list(@AuthenticationPrincipal Jwt jwt) {
    return jobs.list(UUID.fromString(jwt.getSubject()));
  }

  @PostMapping("/jobs")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public JobView start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Start input) {
    return jobs.start(UUID.fromString(jwt.getSubject()), input);
  }

  @GetMapping("/jobs/{id}")
  public JobView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return jobs.get(UUID.fromString(jwt.getSubject()), id);
  }

  @PutMapping("/jobs/{id}/plan")
  public JobView approve(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Plan input) {
    return jobs.approve(UUID.fromString(jwt.getSubject()), id, input);
  }

  @PostMapping("/jobs/{id}/{action}")
  public JobView control(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String action) {
    return jobs.control(UUID.fromString(jwt.getSubject()), id, action);
  }
}
