package com.cognify.repository;

import com.cognify.model.domain.Wallet;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  Optional<Wallet> findByOwnerId(UUID ownerId);
}
