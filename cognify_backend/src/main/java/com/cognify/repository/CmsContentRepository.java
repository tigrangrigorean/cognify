package com.cognify.repository;

import com.cognify.model.domain.CmsContent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CmsContentRepository extends JpaRepository<CmsContent, UUID> {
  Optional<CmsContent> findByContentKey(String key);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CmsContent c where c.contentKey=:key")
  Optional<CmsContent> lockKey(@org.springframework.data.repository.query.Param("key") String key);
}
