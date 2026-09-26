package com.loadedvj.backend.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One retrievable passage of ingested vertical-jump training knowledge. The embedding column
 * (pgvector) is intentionally NOT mapped here -- it's write-once at ingestion and only ever
 * queried through the cosine-distance operator, never pulled into Java, so it's set/searched via
 * KnowledgeVectorRepository's native queries instead of JPA. This entity is for everything else:
 * basic CRUD, listing ingested content, and (for large sources) inserting the row that
 * KnowledgeVectorRepository then attaches an embedding to.
 */
@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** "Accumulation" / "Intensification" / "Realization" (matches MesocycleCalculator.Phase.name()),
     *  or null for phase-agnostic knowledge. */
    private String phase;

    private String topic;

    @Column(columnDefinition = "text")
    private String gist;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getGist() { return gist; }
    public void setGist(String gist) { this.gist = gist; }
    public Instant getCreatedAt() { return createdAt; }
}
