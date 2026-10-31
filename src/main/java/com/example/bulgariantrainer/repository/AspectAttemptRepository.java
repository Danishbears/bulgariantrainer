package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.AspectAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AspectAttemptRepository extends JpaRepository<AspectAttempt,Long> {
    @Query("""
        select new com.example.bulgariantrainer.repository.AspectOutcomeCount(q.direction,a.outcome,count(a))
        from AspectAttempt a join a.question q join q.pair p
        where (:namespace is null or p.dataset.namespace=:namespace)
        group by q.direction,a.outcome
        """)
    List<AspectOutcomeCount> countOutcomes(@Param("namespace") String namespace);
}
