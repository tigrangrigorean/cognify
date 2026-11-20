package com.cognify;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:cognify;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "cognify.jwt-secret=test-only-secret-at-least-32-characters-long"
    })
@AutoConfigureMockMvc
class WorkspaceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String register(String email) throws Exception {
    return mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\"test-password-123\",\"name\":\"Անի\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void authOwnershipAndVersioning() throws Exception {
    var a = json.readTree(register("ani@example.com"));
    String bearer = "Bearer " + a.get("accessToken").asText();
    mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(0));
    String input =
        "{\"title\":\"Research\",\"kind\":\"ESSAY\",\"content\":\"Draft\",\"version\":0}";
    var doc =
        json.readTree(
            mvc.perform(
                    post("/api/documents")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content(input))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String id = doc.get("id").asText();
    var b = json.readTree(register("aram@example.com"));
    String other = "Bearer " + b.get("accessToken").asText();
    mvc.perform(get("/api/documents").header("Authorization", other))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            put("/api/documents/" + id)
                .header("Authorization", other)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/documents/" + id).header("Authorization", other))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/documents/" + id)
                .header("Authorization", bearer)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/documents/" + id)
                .header("Authorization", bearer)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/admin/content/headline")
                .header("Authorization", bearer)
                .contentType("application/json")
                .content("{\"value\":\"No\"}"))
        .andExpect(status().isForbidden());
    String refresh = "{\"refreshToken\":\"" + a.get("refreshToken").asText() + "\"}";
    var rotated =
        json.readTree(
            mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refresh))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refresh))
        .andExpect(status().isUnauthorized());
    String latest = "{\"refreshToken\":\"" + rotated.get("refreshToken").asText() + "\"}";
    mvc.perform(post("/api/auth/logout").contentType("application/json").content(latest))
        .andExpect(status().isOk());
    mvc.perform(post("/api/auth/refresh").contentType("application/json").content(latest))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/me").header("Authorization", "Bearer " + rotated.get("accessToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidRegistrationRejected() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"bad\",\"password\":\"x\",\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
