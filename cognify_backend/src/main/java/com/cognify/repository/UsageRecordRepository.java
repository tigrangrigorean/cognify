package com.cognify.repository;

import com.cognify.model.domain.UsageRecord;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {}
