package com.cognify.repository;

import com.cognify.model.domain.PaymentRecord;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
  Optional<PaymentRecord> findByOwnerIdAndRequestKey(UUID ownerId, String requestKey);

  List<PaymentRecord> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable page);
}
