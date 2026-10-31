package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="aspect_datasets")
public class AspectDataset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=100) private String namespace;
    @Lob @Column(nullable=false) private String metadataJson;
    @Column(length=64) private String contentRevision;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected AspectDataset() {}
    public AspectDataset(String namespace) {
        this.namespace = namespace; metadataJson = "{}";
        createdAt = updatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public void recordImport(String metadata, String revision) {
        metadataJson = metadata; contentRevision = revision; updatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public String getNamespace() { return namespace; }
    public String getMetadataJson() { return metadataJson; }
    public String getContentRevision() { return contentRevision; }
    public long getVersion() { return version; }
}
