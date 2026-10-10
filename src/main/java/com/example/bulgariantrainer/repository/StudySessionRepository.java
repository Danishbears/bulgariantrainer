package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    java.util.Optional<StudySession> findByTypingRequestId(java.util.UUID requestId);
    java.util.Optional<StudySession> findBySentenceRequestId(java.util.UUID requestId);
    java.util.Optional<StudySession> findByFlashcardRequestId(java.util.UUID requestId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from StudySession s where s.id = :id")
    java.util.Optional<StudySession> findForUpdate(@org.springframework.data.repository.query.Param("id") long id);
}

