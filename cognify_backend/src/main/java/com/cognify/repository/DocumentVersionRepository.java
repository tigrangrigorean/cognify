package com.cognify.repository;

import com.cognify.model.domain.DocumentVersion;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
  List<DocumentVersion> findByDocumentIdOrderByRevisionDesc(UUID documentId, Pageable page);
}
