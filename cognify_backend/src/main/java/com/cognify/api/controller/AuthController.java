package com.cognify.api.controller;

import com.cognify.model.dto.Requests.*;
import com.cognify.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService auth;

  @PostMapping("/register")
  public AuthService.AuthResult register(@Valid @RequestBody Register r) {
    return auth.register(r);
  }

  @PostMapping("/login")
  public AuthService.AuthResult login(@Valid @RequestBody Login r) {
    return auth.login(r);
  }

  @PostMapping("/refresh")
  public AuthService.AuthResult refresh(@Valid @RequestBody Token r) {
    return auth.refresh(r.refreshToken());
  }

  @PostMapping("/logout")
  public void logout(@Valid @RequestBody Token r) {
    auth.logout(r.refreshToken());
  }
}
