package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AoristImportReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AoristImportReceiptRepository extends JpaRepository<AoristImportReceipt,UUID> {
    boolean existsByImportId(UUID importId);
}
