package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.TypingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TypingQuestionRepository extends JpaRepository<TypingQuestion, Long> {
    List<TypingQuestion> findBySessionIdOrderByPositionAsc(long sessionId);
    boolean existsByWordId(long wordId);
}
