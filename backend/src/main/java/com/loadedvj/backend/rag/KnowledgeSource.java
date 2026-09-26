package com.loadedvj.backend.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One ingested document -- a YouTube video, a PDF/text study, or a pasted transcript -- that has
 * been split into knowledge_chunk rows. Kept mainly for provenance: which chunks came from where.
 */
@Entity
@Table(name = "knowledge_source")
public class KnowledgeSource {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    private String origin;

    private String title;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getIngestedAt() { return ingestedAt; }
}
