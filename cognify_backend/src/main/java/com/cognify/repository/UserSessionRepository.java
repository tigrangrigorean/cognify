package com.cognify.repository;

import com.cognify.model.domain.UserSession;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
  List<UserSession> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  Optional<UserSession> findByIdAndOwnerId(UUID id, UUID ownerId);
}
