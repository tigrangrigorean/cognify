package com.cognify.service.ai;

import java.util.List;

public interface AiProvider {
  record Request(
      String topic,
      String kind,
      String language,
      int pages,
      String subject,
      String university,
      String requirements,
      List<String> plan,
      String section,
      String previous,
      int targetWords) {}

  record Result(String text, long inputTokens, long outputTokens) {}

  String name();

  String model();

  boolean available();

  boolean mock();

  Result plan(Request request);

  Result section(Request request);
}
