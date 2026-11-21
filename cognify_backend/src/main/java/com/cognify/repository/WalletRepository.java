package com.cognify.repository;

import com.cognify.model.domain.Wallet;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  Optional<Wallet> findByOwnerId(UUID ownerId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.ownerId=:owner")
  Optional<Wallet> lockOwner(@org.springframework.data.repository.query.Param("owner") UUID owner);
}
