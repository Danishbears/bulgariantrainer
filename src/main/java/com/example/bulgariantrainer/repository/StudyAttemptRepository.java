package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyAttemptRepository extends JpaRepository<StudyAttempt, Long> {
    boolean existsByWordId(Long wordId);

    @org.springframework.data.jpa.repository.Query("""
        select new com.example.bulgariantrainer.repository.StudyAttemptSnapshot(
            a.id, s.id, s.trainingMode, a.direction, a.result, a.evaluationSource,
            a.createdAt, w.id)
        from StudyAttempt a join a.session s left join a.word w
        order by a.createdAt asc, a.id asc
        """)
    java.util.List<StudyAttemptSnapshot> findStatisticsRows();

    java.util.List<StudyAttempt> findTop20ByWordIdAndResultOrderByCreatedAtDescIdDesc(
            Long wordId, AttemptResult result);
    long countByWordIdAndResult(Long wordId, AttemptResult result);
}
