package com.cognify.repository;

import com.cognify.model.domain.CoinPackage;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CoinPackageRepository extends JpaRepository<CoinPackage, UUID> {
  List<CoinPackage> findAllByOrderByCoinsAsc();
}
