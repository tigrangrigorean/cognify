package com.cognify.model.dto;

import jakarta.validation.constraints.*;

public class Requests {
  public record Register(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 72) String password,
      @NotBlank @Size(max = 100) String name) {}

  public record Login(@Email @NotBlank String email, @NotBlank @Size(max = 72) String password) {}

  public record Token(@NotBlank @Size(max = 200) String refreshToken) {}

  public record DocumentInput(
      @NotBlank @Size(max = 200) String title,
      @NotNull @Pattern(regexp = "COURSEWORK|THESIS|ESSAY|REPORT") String kind,
      @NotNull @Size(max = 200000) String content,
      long version) {}

  public record CmsInput(@NotBlank @Size(max = 10000) String value) {}
}
