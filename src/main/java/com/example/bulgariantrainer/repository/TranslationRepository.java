package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranslationRepository extends JpaRepository<Translation, Long> {
    long countByWordId(Long wordId);
}
