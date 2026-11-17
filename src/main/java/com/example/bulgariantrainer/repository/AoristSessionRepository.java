package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AoristSessionRepository extends JpaRepository<AoristSession,Long> {
    @Query("select s.id from AoristSession s where s.requestId=:requestId")
    Optional<Long> findIdByRequestId(@Param("requestId") UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AoristSession s where s.id=:id")
    Optional<AoristSession> findForUpdate(@Param("id") long id);
    Page<AoristSession> findByStatus(SessionStatus status,Pageable pageable);
}
