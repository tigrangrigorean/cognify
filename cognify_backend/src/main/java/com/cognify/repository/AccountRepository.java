package com.cognify.repository;

import com.cognify.model.domain.Account;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByEmail(String email);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id=:id")
  Optional<Account> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
