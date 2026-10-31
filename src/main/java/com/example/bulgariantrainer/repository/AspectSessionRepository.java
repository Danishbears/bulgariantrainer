package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AspectSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AspectSessionRepository extends JpaRepository<AspectSession,Long> {
    @Query("select s.id from AspectSession s where s.requestId=:requestId")
    Optional<Long> findIdByRequestId(@Param("requestId") UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AspectSession s where s.id=:id")
    Optional<AspectSession> findForUpdate(@Param("id") long id);
}
