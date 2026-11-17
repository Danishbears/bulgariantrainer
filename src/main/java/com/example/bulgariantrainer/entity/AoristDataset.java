package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity @Table(name="aorist_datasets")
public class AoristDataset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=100, updatable=false) private String namespace;
    @Lob @Column(nullable=false) private String metadataJson;
    @Column(nullable=false) private int datasetRevision;
    @Column(length=64) private String fileHash;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected AoristDataset() {}
    public AoristDataset(String namespace, String metadata, int revision) {
        this.namespace=namespace; metadataJson=metadata; datasetRevision=revision;
        createdAt=updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public void recordImport(String metadata, int revision, String hash) {
        metadataJson=metadata; datasetRevision=revision; fileHash=hash;
        updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public String getNamespace() { return namespace; }
    public String getMetadataJson() { return metadataJson; }
    public int getDatasetRevision() { return datasetRevision; }
    public String getFileHash() { return fileHash; }
    public long getVersion() { return version; }
}
