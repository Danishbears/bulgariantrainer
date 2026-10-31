package com.example.bulgariantrainer.repository;
import com.example.bulgariantrainer.entity.AspectImportReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AspectImportReceiptRepository extends JpaRepository<AspectImportReceipt,UUID> {
    boolean existsByImportId(UUID importId);
}
