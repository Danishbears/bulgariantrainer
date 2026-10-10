package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SentenceQuestionRepository extends JpaRepository<SentenceQuestion,Long> {
 List<SentenceQuestion> findBySessionIdOrderByPositionAsc(long sessionId);
 boolean existsBySentenceId(long sentenceId);
}
