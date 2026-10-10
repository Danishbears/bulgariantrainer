package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.FlashcardQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FlashcardQuestionRepository extends JpaRepository<FlashcardQuestion, Long> {
    Optional<FlashcardQuestion> findByIdAndSessionId(long id, long sessionId);
    Optional<FlashcardQuestion> findFirstBySessionIdAndAttemptIsNullOrderByPositionAsc(long sessionId);
    List<FlashcardQuestion> findBySessionIdOrderByPositionAsc(long sessionId);
    long countBySessionIdAndAttemptIsNotNull(long sessionId);
    boolean existsByWordId(long wordId);
}
