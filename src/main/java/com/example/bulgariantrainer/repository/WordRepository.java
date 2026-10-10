package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.Word;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {
    @Query("select w.id from Word w where w.archived = false and exists (select t.id from Translation t where t.word = w)")
    java.util.List<Long> findTrainableIds();
    boolean existsByNormalizedText(String normalizedText);
    Optional<Word> findByNormalizedText(String normalizedText);
    boolean existsByNormalizedTextAndIdNot(String normalizedText, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Word w where w.id = :id")
    Optional<Word> findForUpdate(@Param("id") Long id);

    @Query("""
            select w from Word w
            where w.archived = :archived
              and (:part is null or lower(w.partOfSpeech) = :part)
              and (:pattern is null or w.normalizedText like :pattern escape '!'
               or exists (select t.id from Translation t
                          where t.word = w and t.normalizedText like :pattern escape '!'))
            """)
    Page<Word> filter(@Param("pattern") String pattern, @Param("part") String part,
                      @Param("archived") boolean archived, Pageable pageable);

    @Query("select distinct w.partOfSpeech from Word w where w.partOfSpeech is not null order by w.partOfSpeech")
    java.util.List<String> findPartsOfSpeech();
}
