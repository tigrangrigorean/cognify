package com.cognify.repository;

import com.cognify.model.domain.GenerationStep;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface GenerationStepRepository extends JpaRepository<GenerationStep, UUID> {
  List<GenerationStep> findByJobIdOrderByPositionAsc(UUID jobId);
}
