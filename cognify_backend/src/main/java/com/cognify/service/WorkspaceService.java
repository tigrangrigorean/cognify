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
  private final DocumentSectionRepository sections;
  private final DocumentHistoryService history;

  public record Doc(
      UUID id,
      String title,
      String kind,
      String content,
      Instant updatedAt,
      long version,
      String generationMode) {}

  public record Profile(String name, String email, long balance, String role) {}

  public static Doc toDto(Document d) {
    return new Doc(
        d.getId(),
        d.getTitle(),
        d.getKind(),
        d.getContent(),
        d.getUpdatedAt(),
        d.getVersion(),
        d.getGenerationMode());
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
    return documents.findByOwnerIdOrderByUpdatedAtDesc(owner).stream()
        .map(WorkspaceService::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Doc get(UUID owner, UUID id) {
    return toDto(
        documents
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @Transactional
  public Doc save(UUID owner, UUID id, DocumentInput input) {
    Document d =
        id == null
            ? new Document()
            : documents
                .lockOwned(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (id != null && d.getVersion() != input.version())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Document changed; reload before saving");
    if (id != null) {
      if (sections.existsByDocumentId(id) && !Objects.equals(input.content(), d.getContent()))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Use the section editor for this document");
      history.snapshot(d);
    }
    d.setOwnerId(owner);
    d.setTitle(input.title());
    d.setKind(input.kind());
    d.setContent(input.content());
    d.setUpdatedAt(Instant.now());
    return toDto(documents.saveAndFlush(d));
  }

  @Transactional
  public void delete(UUID owner, UUID id) {
    documents.delete(
        documents
            .lockOwned(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }
}
