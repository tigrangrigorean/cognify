package com.cognify.repository;

import com.cognify.model.domain.CmsContent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CmsContentRepository extends JpaRepository<CmsContent, UUID> {
  Optional<CmsContent> findByContentKey(String key);
}
