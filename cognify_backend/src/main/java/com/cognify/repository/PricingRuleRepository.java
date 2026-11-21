package com.cognify.repository;

import com.cognify.model.domain.PricingRule;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
  Optional<PricingRule> findByOperation(String operation);

  List<PricingRule> findAllByOrderByOperationAsc();
}
