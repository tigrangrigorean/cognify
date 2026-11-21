package com.cognify.service;

import com.cognify.model.domain.*;
import com.cognify.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentHistoryService {
  private final DocumentVersionRepository versions;

  public void snapshot(Document d) {
    DocumentVersion v = new DocumentVersion();
    v.setDocumentId(d.getId());
    v.setRevision(d.getVersion());
    v.setTitle(d.getTitle());
    v.setContent(d.getContent());
    versions.save(v);
  }
}
