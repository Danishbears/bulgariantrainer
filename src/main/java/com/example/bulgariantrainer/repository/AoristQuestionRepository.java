package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AoristQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AoristQuestionRepository extends JpaRepository<AoristQuestion,Long> {
    List<AoristQuestion> findBySessionIdOrderByPositionAsc(Long sessionId);
}
