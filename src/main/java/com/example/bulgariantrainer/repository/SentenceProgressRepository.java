package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SentenceProgressRepository extends JpaRepository<SentenceProgress,Long> {
 Optional<SentenceProgress> findBySentenceId(long sentenceId);
 List<SentenceProgress> findBySentenceIdIn(Collection<Long> sentenceIds);
}
