package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface AspectProgressRepository extends JpaRepository<AspectProgress,Long> {
    Optional<AspectProgress> findByPairIdAndDirection(Long pairId,AspectDirection direction);
    List<AspectProgress> findByPairIdIn(Collection<Long> ids);
    @Query("select p from AspectProgress p join fetch p.pair a join fetch a.dataset where (:namespace is null or a.dataset.namespace=:namespace) order by a.id,p.direction")
    List<AspectProgress> findForNamespace(@org.springframework.data.repository.query.Param("namespace") String namespace);
}
