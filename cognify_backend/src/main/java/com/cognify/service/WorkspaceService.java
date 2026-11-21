package com.cognify.service;

import com.cognify.model.domain.Document;
import com.cognify.model.dto.Requests.DocumentInput;
import com.cognify.repository.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
  private final DocumentRepository documents;
  private final WalletRepository wallets;
  private final AccountRepository accounts;

  public record Doc(
      UUID id, String title, String kind, String content, Instant updatedAt, long version) {}

  public record Profile(String name, String email, long balance, String role) {}

  private Doc dto(Document d) {
    return new Doc(
        d.getId(), d.getTitle(), d.getKind(), d.getContent(), d.getUpdatedAt(), d.getVersion());
  }

  @Transactional(readOnly = true)
  public Profile profile(UUID owner) {
    var a = accounts.findById(owner).orElseThrow();
    var wallet = wallets.findByOwnerId(owner).orElseThrow();
    return new Profile(
        a.getName(), a.getEmail(), wallet.getBalance() - wallet.getReserved(), a.getRole());
  }

  @Transactional(readOnly = true)
  public List<Doc> list(UUID owner) {
    return documents.findByOwnerIdOrderByUpdatedAtDesc(owner).stream().map(this::dto).toList();
  }

  @Transactional
  public Doc save(UUID owner, UUID id, DocumentInput input) {
    Document d =
        id == null
            ? new Document()
            : documents
                .findByIdAndOwnerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (id != null && d.getVersion() != input.version())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Document changed; reload before saving");
    d.setOwnerId(owner);
    d.setTitle(input.title());
    d.setKind(input.kind());
    d.setContent(input.content());
    d.setUpdatedAt(Instant.now());
    return dto(documents.saveAndFlush(d));
  }

  @Transactional
  public void delete(UUID owner, UUID id) {
    documents.delete(
        documents
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }
}
