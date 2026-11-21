package com.cognify.repository;

import com.cognify.model.domain.GenerationJob;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
  Optional<GenerationJob> findByOwnerIdAndRequestKey(UUID ownerId, String key);

  Optional<GenerationJob> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<GenerationJob> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable page);

  long countByOwnerIdAndStatusIn(UUID ownerId, Collection<String> statuses);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from GenerationJob j where j.id=:id")
  Optional<GenerationJob> lock(@Param("id") UUID id);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select j from GenerationJob j where j.status='QUEUED' or (j.status='RUNNING' and"
          + " j.leaseUntil<:now) order by j.createdAt")
  List<GenerationJob> claimable(@Param("now") java.time.Instant now, Pageable page);
}
