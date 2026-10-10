package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyProgressRepository extends JpaRepository<StudyProgress, Long> {
    boolean existsByWordId(Long wordId);
    java.util.Optional<StudyProgress> findByWordIdAndTrainingMode(Long wordId, TrainingMode trainingMode);
    java.util.List<StudyProgress> findByTrainingModeAndWordIdIn(TrainingMode trainingMode, java.util.Collection<Long> wordIds);
}
