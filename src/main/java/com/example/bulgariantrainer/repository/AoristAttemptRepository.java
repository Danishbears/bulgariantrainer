package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AoristAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AoristAttemptRepository extends JpaRepository<AoristAttempt,Long> {
    @Query("""
        select new com.example.bulgariantrainer.repository.AoristOutcomeCount(s.exerciseType,q.person,a.outcome,count(a))
        from AoristAttempt a join a.question q join q.session s join q.verb v
        where (:datasetId is null or v.dataset.namespace=:datasetId)
        group by s.exerciseType,q.person,a.outcome
        """)
    List<AoristOutcomeCount> countOutcomes(@Param("datasetId") String datasetId);
}
