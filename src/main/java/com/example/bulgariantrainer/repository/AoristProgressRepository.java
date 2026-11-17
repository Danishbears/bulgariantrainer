package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AoristProgressRepository extends JpaRepository<AoristProgress,Long> {
    Optional<AoristProgress> findByVerbIdAndPersonAndExerciseType(Long verbId,String person,AoristExerciseType type);
    List<AoristProgress> findByVerbIdIn(Collection<Long> ids);
    @Query("select p from AoristProgress p join fetch p.verb v join fetch v.dataset where (:datasetId is null or v.dataset.namespace=:datasetId) order by v.id,p.person,p.exerciseType")
    List<AoristProgress> findForDataset(@Param("datasetId") String datasetId);
}
