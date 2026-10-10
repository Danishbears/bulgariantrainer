package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentenceWordRepository extends JpaRepository<SentenceWord, SentenceWordId> {
    boolean existsByWordId(Long wordId);
    java.util.List<SentenceWord> findBySentenceId(Long sentenceId);
    void deleteBySentenceId(Long sentenceId);
}

