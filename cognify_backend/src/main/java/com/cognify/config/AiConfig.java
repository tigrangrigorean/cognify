package com.cognify.config;

import com.cognify.service.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class AiConfig {
  @Bean
  public AiProvider aiProvider(
      Environment env,
      ObjectMapper mapper,
      @Value("${cognify.ai.mode:disabled}") String mode,
      @Value("${cognify.ai.key:}") String key,
      @Value("${cognify.ai.model:gpt-4.1-mini}") String model,
      @Value("${cognify.ai.base:https://api.openai.com/v1}") String base) {
    if (mode.equals("openai")) return new OpenAiProvider(key, model, base, mapper);
    if (!Set.of("disabled", "mock").contains(mode))
      throw new IllegalArgumentException("Unsupported AI_MODE");
    boolean mock = mode.equals("mock");
    Set<String> profiles = Set.of(env.getActiveProfiles());
    if (mock && (!profiles.contains("dev") || profiles.contains("prod")))
      throw new IllegalStateException("Mock AI requires dev and is forbidden in prod");
    return new AiProvider() {
      public String name() {
        return mock ? "MOCK" : "DISABLED";
      }

      public String model() {
        return mock ? "mock-v1" : "";
      }

      public boolean available() {
        return mock;
      }

      public boolean mock() {
        return mock;
      }

      public Result plan(Request r) {
        if (!mock) throw new AiFailure("PROVIDER_UNAVAILABLE");
        return new Result(
            "{\"headings\":[\"Ներածություն\",\"Հիմնական քննարկում\",\"Եզրակացություն\"]}", 0, 0);
      }

      public Result section(Request r) {
        if (!mock) throw new AiFailure("PROVIDER_UNAVAILABLE");
        return new Result(
            "ԹԵՍՏԱՅԻՆ ԲՈՎԱՆԴԱԿՈՒԹՅՈՒՆ — սա իրական AI գեներացիա չէ։\n\nԹեմա՝ "
                + r.topic()
                + "։ Բաժին՝ "
                + r.section()
                + "։ Այս տեքստը նախատեսված է միայն ստեղծման, խմբագրման և արտահանման ընթացքը"
                + " ստուգելու համար։ Այն չի հանդիսանում ակադեմիական հետազոտություն և չի պարունակում"
                + " հաստատված աղբյուրներ։",
            0,
            0);
      }
    };
  }
}
