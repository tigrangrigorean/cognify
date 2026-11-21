package com.cognify.repository;

import com.cognify.model.domain.DocumentSection;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentSectionRepository extends JpaRepository<DocumentSection, UUID> {
  List<DocumentSection> findByDocumentIdOrderByPositionAsc(UUID documentId);

  Optional<DocumentSection> findByIdAndDocumentId(UUID id, UUID documentId);

  boolean existsByDocumentId(UUID documentId);
}
