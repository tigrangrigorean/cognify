package com.cognify.repository;

import com.cognify.model.domain.CoinTransaction;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, UUID> {
  Optional<CoinTransaction> findByOwnerIdAndRequestKey(UUID ownerId, String requestKey);

  List<CoinTransaction> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable page);
}
