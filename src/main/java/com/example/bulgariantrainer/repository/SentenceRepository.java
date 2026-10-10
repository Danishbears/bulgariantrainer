package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface SentenceRepository extends JpaRepository<Sentence,Long> {
 Optional<Sentence> findByNormalizedRussian(String key);
 boolean existsByNormalizedRussianAndIdNot(String key,Long id);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from Sentence s where s.id=:id") Optional<Sentence> findForUpdate(@Param("id") long id);
 Page<Sentence> findByArchived(boolean archived,Pageable pageable);
 @Query("select s.id from Sentence s where s.approved=true and s.archived=false order by s.id") List<Long> findTrainableIds();
 @Query("select distinct sw.sentence.id from SentenceWord sw join StudyProgress p on p.word.id=sw.word.id where sw.sentence.approved=true and sw.sentence.archived=false and p.totalAttempts>0 and (p.nextReviewAt<=:now or p.nextReviewAt is null)")
 List<Long> findDueLinkedIds(@Param("now") Instant now);
}
