package com.cognify.api.controller;

import com.cognify.model.dto.Requests.*;
import com.cognify.service.PasswordResetService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {
  private final PasswordResetService reset;

  @PostMapping("/forgot-password")
  public Map<String, String> request(@Valid @RequestBody ResetRequest request) {
    reset.request(request);
    return Map.of("message", "Եթե այդ էլ․ հասցեով հաշիվ կա, վերականգնման հղումը կուղարկվի։");
  }

  @PostMapping("/reset-password")
  public void confirm(@Valid @RequestBody ResetConfirm request) {
    reset.confirm(request);
  }
}
