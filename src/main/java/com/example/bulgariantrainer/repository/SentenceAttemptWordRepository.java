package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SentenceAttemptWordRepository extends JpaRepository<SentenceAttemptWord,SentenceAttemptWordId> {
 long countByWordId(long wordId);
}
