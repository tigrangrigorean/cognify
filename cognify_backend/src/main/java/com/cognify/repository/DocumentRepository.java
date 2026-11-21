package com.cognify.repository;

import com.cognify.model.domain.Document;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Document d where d.id=:id and d.ownerId=:owner")
  Optional<Document> lockOwned(
      @org.springframework.data.repository.query.Param("id") UUID id,
      @org.springframework.data.repository.query.Param("owner") UUID owner);

  List<Document> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

  Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);
}
