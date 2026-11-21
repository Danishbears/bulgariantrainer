package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AoristVerb;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AoristVerbRepository extends JpaRepository<AoristVerb,Long>, JpaSpecificationExecutor<AoristVerb> {
    @Query("select v.id from AoristVerb v where v.archived=false and (:datasetId is null or v.dataset.namespace=:datasetId) and (:aspect is null or v.aspect=:aspect) and (:studyGroup is null or v.studyGroup=:studyGroup) order by v.id")
    List<Long> findPracticeIds(@Param("datasetId") String datasetId,@Param("aspect") String aspect,@Param("studyGroup") String studyGroup);
    Optional<AoristVerb> findByDatasetIdAndExternalId(Long datasetId,String externalId);
    @Query("select v.dataset.namespace from AoristVerb v where v.id=:id")
    Optional<String> findNamespace(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AoristVerb v where v.id=:id")
    Optional<AoristVerb> findForUpdate(@Param("id") long id);
    @Query("select v from AoristVerb v where v.dataset.namespace=:namespace order by v.externalId")
    List<AoristVerb> findForExport(@Param("namespace") String namespace);
    @Query("select distinct v.studyGroup from AoristVerb v where (:namespace is null or v.dataset.namespace=:namespace) order by v.studyGroup")
    List<String> findGroups(@Param("namespace") String namespace);
    @Query("select distinct v.aspect from AoristVerb v where (:namespace is null or v.dataset.namespace=:namespace) order by v.aspect")
    List<String> findAspects(@Param("namespace") String namespace);
}
