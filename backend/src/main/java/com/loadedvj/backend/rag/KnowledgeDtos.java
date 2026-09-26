package com.loadedvj.backend.rag;

import java.util.List;
import java.util.UUID;

public final class KnowledgeDtos {

    private KnowledgeDtos() { }

    public record IngestYoutubeRequest(String youtubeUrl) { }

    public record IngestTextRequest(String title, String text) { }

    /** Returned so the ingester can spot-check the auto-assigned tags rather than trusting them blind. */
    public record ChunkSummary(UUID chunkId, String phase, String topic, String gist) { }

    public record IngestResult(UUID sourceId, int chunkCount, List<ChunkSummary> chunks) { }
}
