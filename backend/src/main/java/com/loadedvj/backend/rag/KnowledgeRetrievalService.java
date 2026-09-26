package com.loadedvj.backend.rag;

import com.loadedvj.backend.rag.KnowledgeVectorRepository.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieval side of RAG for generateNextWeek(): embeds a query built from the current training
 * phase and pulls the nearest knowledge chunks (phase-specific plus phase-agnostic ones). Kept
 * separate from KnowledgeVectorRepository so the embedding call and the "how do we build the
 * query text" decision live with the caller of retrieval, not the raw SQL.
 */
@Service
public class KnowledgeRetrievalService {

    private static final int TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final KnowledgeVectorRepository vectorRepository;

    public KnowledgeRetrievalService(EmbeddingService embeddingService, KnowledgeVectorRepository vectorRepository) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    public List<RetrievedChunk> retrieveForPhase(String phaseName, String phaseDescription) {
        String query = phaseName + ": " + phaseDescription;
        float[] queryEmbedding = embeddingService.embedQuery(query);
        return vectorRepository.search(queryEmbedding, phaseName, TOP_K);
    }

    /** One block per chunk: its gist (what it's about) followed by its full content. */
    public static String formatForPrompt(List<RetrievedChunk> chunks) {
        return chunks.stream()
            .map(c -> "- " + (c.gist() != null && !c.gist().isBlank() ? c.gist() : c.topic()) + "\n" + c.content())
            .collect(Collectors.joining("\n\n"));
    }

    public static String chunkIdsCsv(List<RetrievedChunk> chunks) {
        return chunks.isEmpty() ? null : chunks.stream()
            .map(c -> c.id().toString())
            .collect(Collectors.joining(","));
    }
}
