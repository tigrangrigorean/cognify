package com.cognify;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cognify.repository.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:cms;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "cognify.jwt-secret=test-only-secret-at-least-32-characters-long"
    })
@AutoConfigureMockMvc
class CmsIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired AccountRepository accounts;
  @Autowired AuditLogRepository audit;

  private String login(boolean admin) throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    String body =
        mapper.writeValueAsString(
            Map.of("email", email, "password", "long-password-123", "name", "Անի"));
    var registered =
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
    if (!admin)
      return "Bearer "
          + mapper
              .readTree(registered.getResponse().getContentAsString())
              .get("accessToken")
              .asText();
    var a = accounts.findByEmail(email).orElseThrow();
    a.setRole("ADMIN");
    accounts.save(a);
    return "Bearer "
        + mapper
            .readTree(
                mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("accessToken")
            .asText();
  }

  @Test
  void roleIsolationAndPublicVisibility() throws Exception {
    String user = login(false), admin = login(true);
    mvc.perform(get("/api/admin/blocks")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/blocks").header("Authorization", user))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/audit").header("Authorization", user))
        .andExpect(status().isForbidden());
    long before = audit.count();
    var blocks =
        mapper.readTree(
            mvc.perform(get("/api/admin/blocks").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long version = -1;
    for (var block : blocks)
      if (block.get("key").asText().equals("faq")) version = block.get("version").asLong();
    assertTrue(version >= 0);
    String payload =
        mapper.writeValueAsString(
            Map.of(
                "title",
                "Updated FAQ",
                "body",
                "Description",
                "items",
                List.of(Map.of("title", "Question", "body", "Answer")),
                "position",
                1,
                "visible",
                false,
                "version",
                version));
    mvc.perform(
            put("/api/admin/blocks/faq")
                .header("Authorization", user)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/admin/blocks/faq")
                .header("Authorization", admin)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(version + 1));
    var visible =
        mapper.readTree(
            mvc.perform(get("/api/public/blocks"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (var b : visible) assertNotEquals("faq", b.get("key").asText());
    assertEquals(before + 1, audit.count());
    mvc.perform(
            put("/api/admin/blocks/faq")
                .header("Authorization", admin)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isConflict());
    assertEquals(before + 1, audit.count());
    String published =
        payload
            .replace("\"visible\":false", "\"visible\":true")
            .replace("\"version\":" + version, "\"version\":" + (version + 1));
    mvc.perform(
            put("/api/admin/blocks/faq")
                .header("Authorization", admin)
                .contentType("application/json")
                .content(published))
        .andExpect(status().isOk());
    mvc.perform(get("/api/public/blocks"))
        .andExpect(jsonPath("$[1].key").value("faq"))
        .andExpect(jsonPath("$[1].items[0].body").value("Answer"));
    mvc.perform(get("/api/admin/audit").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].target").value("faq"));
  }

  @Test
  void settingsAreAuditedAndInvalidUrlsRejected() throws Exception {
    String admin = login(true);
    long before = audit.count();
    mvc.perform(
            put("/api/admin/content/contact")
                .header("Authorization", admin)
                .contentType("application/json")
                .content("{\"value\":\"support@example.com\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/public/content"))
        .andExpect(jsonPath("$.contact").value("support@example.com"));
    assertEquals(before + 1, audit.count());
    for (String url :
        List.of("javascript:alert(1)", "//evil.example/logo.svg", "https://bad host/logo.svg"))
      mvc.perform(
              put("/api/admin/content/logoUrl")
                  .header("Authorization", admin)
                  .contentType("application/json")
                  .content(mapper.writeValueAsString(Map.of("value", url))))
          .andExpect(status().isBadRequest());
    assertEquals(before + 1, audit.count());
    mvc.perform(
            put("/api/admin/content/logoUrl")
                .header("Authorization", admin)
                .contentType("application/json")
                .content("{\"value\":\"https://assets.example.com/logo.svg\"}"))
        .andExpect(status().isOk());
  }
}
