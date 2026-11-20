package com.cognify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cognify.repository.*;
import com.cognify.service.ResetMailService;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "cognify.jwt-secret=test-only-secret-at-least-32-characters-long"
    })
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired PasswordResetRepository resets;
  @Autowired AccountRepository accounts;
  @MockitoBean ResetMailService mail;

  private JsonNode register(String email) throws Exception {
    return mapper.readTree(
        mvc.perform(
                post("/api/auth/register")
                    .contentType("application/json")
                    .content(
                        mapper.writeValueAsString(
                            Map.of(
                                "email", email, "password", "long-password-123", "name", "Անի"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String request(String email) throws Exception {
    mvc.perform(
            post("/api/auth/forgot-password")
                .contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isOk());
    ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
    verify(mail).send(eq(email), token.capture());
    return token.getValue();
  }

  @Test
  void singleUseResetRevokesSessionsAndDoesNotExposeToken() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    var a = register(email);
    String token = request(email);
    var stored = resets.findByOwnerId(accounts.findByEmail(email).orElseThrow().getId()).get(0);
    assertNotEquals(token, stored.getTokenHash());
    String payload =
        mapper.writeValueAsString(Map.of("token", token, "newPassword", "new-password-123"));
    mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(payload))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + a.get("accessToken").asText()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(payload))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of("email", email, "password", "long-password-123"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of("email", email, "password", "new-password-123"))))
        .andExpect(status().isOk());
  }

  @Test
  void expiredResetAndUnknownAccount() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    register(email);
    String token = request(email);
    var stored = resets.findByOwnerId(accounts.findByEmail(email).orElseThrow().getId()).get(0);
    stored.setExpiresAt(Instant.now().minusSeconds(1));
    resets.save(stored);
    mvc.perform(
            post("/api/auth/reset-password")
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of("token", token, "newPassword", "new-password-123"))))
        .andExpect(status().isBadRequest());
    clearInvocations(mail);
    mvc.perform(
            post("/api/auth/forgot-password")
                .contentType("application/json")
                .content("{\"email\":\"unknown@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").doesNotExist());
    verify(mail, never()).send(anyString(), anyString());
  }
}
