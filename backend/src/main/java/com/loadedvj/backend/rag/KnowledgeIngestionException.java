package com.loadedvj.backend.rag;

/** Any failure while extracting, normalizing, tagging, or embedding an ingested knowledge source. */
public class KnowledgeIngestionException extends RuntimeException {
    public KnowledgeIngestionException(String message) {
        super(message);
    }

    public KnowledgeIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
