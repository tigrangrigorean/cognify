package com.cognify.repository;

import com.cognify.model.domain.AuditLog;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  List<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
}
