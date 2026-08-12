package com.loadedvj.backend.anthropic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Prompt content lives in the database, not in source, so it can be reviewed or edited (via the
 * Supabase SQL editor) without a code deploy. The app only ever reads these -- there's
 * intentionally no setter/update path from application code.
 */
@Entity
@Table(name = "prompt_templates")
public class PromptTemplate {

    @Id
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getKey() { return key; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
}
