package com.cognify.service.ai;

public class AiFailure extends RuntimeException {
  private final String code;

  public AiFailure(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
