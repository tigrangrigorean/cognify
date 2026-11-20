package com.cognify.repository;

import com.cognify.model.domain.RefreshSession;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from RefreshSession s where s.tokenHash=:hash")
  Optional<RefreshSession> lockToken(@Param("hash") String hash);

  List<RefreshSession> findByOwnerId(UUID ownerId);
}
