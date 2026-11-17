package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AoristDataset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AoristDatasetRepository extends JpaRepository<AoristDataset,Long> {
    Optional<AoristDataset> findByNamespace(String namespace);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AoristDataset d where d.namespace=:namespace")
    Optional<AoristDataset> findForUpdate(@Param("namespace") String namespace);
}
