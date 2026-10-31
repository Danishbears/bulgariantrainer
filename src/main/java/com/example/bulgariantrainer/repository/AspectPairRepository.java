package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.AspectPair;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AspectPairRepository extends JpaRepository<AspectPair,Long>, JpaSpecificationExecutor<AspectPair> {
    Optional<AspectPair> findByDatasetIdAndExternalId(Long datasetId,String externalId);
    @Query("select p.dataset.namespace from AspectPair p where p.id=:id")
    Optional<String> findNamespace(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AspectPair p where p.id=:id")
    Optional<AspectPair> findForUpdate(@Param("id") long id);
    @Query("""
        select p.id from AspectPair p where p.archived=false and p.studyStage in :stages
        and (:namespace is null or p.dataset.namespace=:namespace) and (:topic is null or p.topic=:topic)
        order by p.id
        """)
    List<Long> findPracticeIds(@Param("namespace") String namespace,@Param("stages") Collection<Integer> stages,@Param("topic") String topic);
}
