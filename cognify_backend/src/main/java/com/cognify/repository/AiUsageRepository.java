package com.cognify.repository;

import com.cognify.model.domain.AiUsage;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {
  List<AiUsage> findByJobId(UUID jobId);
}
