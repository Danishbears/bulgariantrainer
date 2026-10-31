package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.AspectDataset;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AspectDatasetRepository extends JpaRepository<AspectDataset,Long> {
    Optional<AspectDataset> findByNamespace(String namespace);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AspectDataset d where d.namespace=:namespace")
    Optional<AspectDataset> findForUpdate(@Param("namespace") String namespace);
}
