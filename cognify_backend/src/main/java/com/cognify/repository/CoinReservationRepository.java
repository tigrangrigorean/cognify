package com.cognify.repository;

import com.cognify.model.domain.CoinReservation;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CoinReservationRepository extends JpaRepository<CoinReservation, UUID> {
  Optional<CoinReservation> findByOwnerIdAndRequestKey(UUID ownerId, String requestKey);

  Optional<CoinReservation> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<CoinReservation> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable page);
}
