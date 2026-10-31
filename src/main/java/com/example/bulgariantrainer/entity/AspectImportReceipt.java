package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="aspect_import_receipts")
public class AspectImportReceipt {
    @Id private UUID requestId;
    @Column(nullable=false,unique=true) private UUID importId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="dataset_id",nullable=false) private AspectDataset dataset;
    @Column(nullable=false,length=64) private String requestHash;
    @Column(nullable=false,length=64) private String fileHash;
    @Lob @Column(nullable=false) private String metadataJson;
    @Lob @Column(nullable=false) private String summaryJson;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected AspectImportReceipt() {}
    public AspectImportReceipt(UUID requestId,UUID importId,AspectDataset dataset,String requestHash,
                               String fileHash,String metadata,String summary) {
        this.requestId=requestId; this.importId=importId; this.dataset=dataset;
        this.requestHash=requestHash; this.fileHash=fileHash; metadataJson=metadata;
        summaryJson=summary; createdAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public String getRequestHash(){return requestHash;} public String getSummaryJson(){return summaryJson;}
}
