package com.cognify.repository;

import com.cognify.model.domain.Document;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
  List<Document> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

  Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);
}
