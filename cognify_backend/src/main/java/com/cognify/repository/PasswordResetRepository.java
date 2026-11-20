package com.cognify.repository;

import com.cognify.model.domain.PasswordReset;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
  @Query("select r.ownerId from PasswordReset r where r.tokenHash=:hash")
  Optional<UUID> findOwner(@Param("hash") String hash);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from PasswordReset r where r.tokenHash=:hash")
  Optional<PasswordReset> lockToken(@Param("hash") String hash);

  List<PasswordReset> findByOwnerId(UUID ownerId);
}
