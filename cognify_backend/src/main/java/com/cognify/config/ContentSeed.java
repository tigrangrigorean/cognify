package com.cognify.config;

import com.cognify.model.domain.CmsContent;
import com.cognify.repository.CmsContentRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentSeed implements CommandLineRunner {
  private final CmsContentRepository content;

  public void run(String... args) {
    Map.of(
            "headline",
            "Գաղափարից՝ մինչև պատրաստ աշխատանք։",
            "subtitle",
            "Միասնական տարածք՝ ակադեմիական փաստաթղթեր ստեղծելու, խմբագրելու և ձեր պահանջներին"
                + " համապատասխան ձևավորելու համար։",
            "footer",
            "Ակադեմիական աշխատանքի ձեր անձնական տարածքը։")
        .forEach(
            (key, value) -> {
              if (content.findByContentKey(key).isEmpty()) {
                CmsContent c = new CmsContent();
                c.setContentKey(key);
                c.setValue(value);
                content.save(c);
              }
            });
  }
}
