package com.cognify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sessions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "cognify.jwt-secret=test-only-secret-at-least-32-characters-long"
    })
@AutoConfigureMockMvc
class SessionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private JsonNode auth(String path, String email, String password) throws Exception {
    return mapper.readTree(
        mvc.perform(
                post("/api/auth/" + path)
                    .contentType("application/json")
                    .content(
                        mapper.writeValueAsString(
                            Map.of("email", email, "password", password, "name", "Անի"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void revokeIsIsolatedAndImmediatelyInvalidatesJwt() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    var a = auth("register", email, "long-password-123");
    var b = auth("login", email, "long-password-123");
    var other = auth("register", UUID.randomUUID() + "@example.com", "long-password-123");
    String ba = "Bearer " + a.get("accessToken").asText(),
        bb = "Bearer " + b.get("accessToken").asText();
    var list =
        mapper.readTree(
            mvc.perform(get("/api/account/sessions").header("Authorization", ba))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString());
    String id = "";
    for (var s : list) if (s.get("current").asBoolean()) id = s.get("id").asText();
    mvc.perform(
            delete("/api/account/sessions/" + id)
                .header("Authorization", "Bearer " + other.get("accessToken").asText()))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/account/sessions/" + id).header("Authorization", bb))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization", ba)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header("Authorization", bb)).andExpect(status().isOk());
    mvc.perform(delete("/api/account/sessions").header("Authorization", bb))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization", bb)).andExpect(status().isUnauthorized());
  }

  @Test
  void passwordChangeRevokesAllSessionsAndRejectsOldPassword() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    var a = auth("register", email, "long-password-123");
    var b = auth("login", email, "long-password-123");
    String bearer = "Bearer " + a.get("accessToken").asText();
    mvc.perform(
            post("/api/account/password")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-123\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/account/password")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content(
                    "{\"currentPassword\":\"long-password-123\",\"newPassword\":\"new-password-123\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + b.get("accessToken").asText()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of("email", email, "password", "long-password-123"))))
        .andExpect(status().isUnauthorized());
    auth("login", email, "new-password-123");
  }
}
