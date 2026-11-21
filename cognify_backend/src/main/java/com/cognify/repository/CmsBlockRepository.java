package com.cognify.repository;

import com.cognify.model.domain.CmsBlock;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CmsBlockRepository extends JpaRepository<CmsBlock, UUID> {
  List<CmsBlock> findAllByOrderByPositionAscBlockKeyAsc();

  Optional<CmsBlock> findByBlockKey(String key);
}
