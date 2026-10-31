package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.AspectQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AspectQuestionRepository extends JpaRepository<AspectQuestion,Long> {
    List<AspectQuestion> findBySessionIdOrderByPositionAsc(long sessionId);
}
