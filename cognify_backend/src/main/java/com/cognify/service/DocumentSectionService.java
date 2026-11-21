package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.model.dto.GenerationDtos.*;
import com.cognify.repository.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentSectionService {
  private final DocumentRepository documents;
  private final DocumentSectionRepository sections;
  private final DocumentVersionRepository versions;
  private final DocumentHistoryService history;

  @Transactional(readOnly = true)
  public List<SectionView> list(UUID owner, UUID docId) {
    owned(owner, docId);
    return sections.findByDocumentIdOrderByPositionAsc(docId).stream()
        .map(s -> new SectionView(s.getId(), s.getPosition(), s.getTitle(), s.getContent()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<VersionView> history(UUID owner, UUID docId) {
    owned(owner, docId);
    return versions.findByDocumentIdOrderByRevisionDesc(docId, PageRequest.of(0, 10)).stream()
        .map(
            v ->
                new VersionView(
                    v.getId(), v.getRevision(), v.getTitle(), v.getContent(), v.getCreatedAt()))
        .toList();
  }

  @Transactional
  public WorkspaceService.Doc save(UUID owner, UUID docId, UUID sectionId, SectionInput input) {
    Document d = lock(owner, docId, input.version());
    List<DocumentSection> all = sections.findByDocumentIdOrderByPositionAsc(docId);
    if (sectionId == null && all.size() >= 30) throw error(400, "Section limit reached");
    history.snapshot(d);
    DocumentSection s;
    if (sectionId == null) {
      if (all.isEmpty() && !d.getContent().isBlank()) {
        if (d.getContent().length() > 16000)
          throw error(400, "Shorten the draft before adding sections");
        DocumentSection original = new DocumentSection();
        original.setDocumentId(docId);
        original.setTitle("Բովանդակություն");
        original.setContent(d.getContent());
        original.setPosition(0);
        sections.save(original);
        all.add(original);
      }
      s = new DocumentSection();
      s.setDocumentId(docId);
      s.setPosition(all.size());
      all.add(s);
    } else
      s =
          all.stream()
              .filter(x -> x.getId().equals(sectionId))
              .findFirst()
              .orElseThrow(() -> error(404, "Section not found"));
    s.setTitle(input.title());
    s.setContent(input.content());
    sections.save(s);
    return rebuild(d, all);
  }

  @Transactional
  public WorkspaceService.Doc delete(UUID owner, UUID docId, UUID id, long version) {
    Document d = lock(owner, docId, version);
    var all = sections.findByDocumentIdOrderByPositionAsc(docId);
    var s =
        all.stream()
            .filter(x -> x.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> error(404, "Section not found"));
    history.snapshot(d);
    sections.delete(s);
    all.remove(s);
    return rebuild(d, all);
  }

  @Transactional
  public WorkspaceService.Doc reorder(UUID owner, UUID docId, Order input) {
    Document d = lock(owner, docId, input.version());
    var all = sections.findByDocumentIdOrderByPositionAsc(docId);
    var ids = all.stream().map(DocumentSection::getId).toList();
    if (input.ids().size() != ids.size()
        || new HashSet<>(input.ids()).size() != ids.size()
        || !new HashSet<>(input.ids()).equals(new HashSet<>(ids)))
      throw error(400, "Invalid section order");
    history.snapshot(d);
    Map<UUID, DocumentSection> byId = new HashMap<>();
    for (var s : all) byId.put(s.getId(), s);
    return rebuild(d, input.ids().stream().map(byId::get).toList());
  }

  private WorkspaceService.Doc rebuild(Document d, List<DocumentSection> all) {
    for (int i = 0; i < all.size(); i++) all.get(i).setPosition(i);
    String full =
        all.stream()
            .map(s -> s.getTitle() + "\n\n" + s.getContent())
            .reduce((a, b) -> a + "\n\n" + b)
            .orElse("");
    if (full.length() > 200000) throw error(400, "Document too large");
    d.setContent(full);
    d.setUpdatedAt(Instant.now());
    return WorkspaceService.toDto(documents.saveAndFlush(d));
  }

  private Document owned(UUID owner, UUID id) {
    return documents
        .findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> error(404, "Document not found"));
  }

  private Document lock(UUID owner, UUID id, long version) {
    Document d = documents.lockOwned(id, owner).orElseThrow(() -> error(404, "Document not found"));
    if (d.getVersion() != version) throw error(409, "Document changed. Reload before editing");
    return d;
  }

  private ResponseStatusException error(int code, String text) {
    return new ResponseStatusException(HttpStatus.valueOf(code), text);
  }
}
