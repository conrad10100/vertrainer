package com.loadedvj.backend.rag;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Owns the pgvector-specific side of knowledge_chunk (the embedding column and its
 * cosine-distance search) via native SQL, kept separate from KnowledgeChunkRepository's plain
 * JPA CRUD -- same split ApiUsageDailyRepository uses for its own native upsert. This is the one
 * place in the codebase that talks to the `vector` column type; it's also the only piece that
 * needs a real Postgres (not H2) to test, since H2 has no vector type or `<=>` operator.
 */
@Repository
public class KnowledgeVectorRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Attaches an embedding to an already-saved chunk. */
    public void setEmbedding(UUID chunkId, float[] embedding) {
        entityManager.createNativeQuery(
                "update knowledge_chunk set embedding = cast(:vec as vector) where id = :id")
            .setParameter("vec", toVectorLiteral(embedding))
            .setParameter("id", chunkId)
            .executeUpdate();
    }

    /**
     * Nearest neighbors by cosine distance, restricted to chunks tagged for the given phase plus
     * phase-agnostic ones (phase is null) -- so a query for "INTENSIFICATION" also pulls in
     * general knowledge that applies regardless of phase. Chunks with no embedding yet (a save
     * that hasn't reached setEmbedding, or a failed embed call) are excluded.
     */
    @SuppressWarnings("unchecked")
    public List<RetrievedChunk> search(float[] queryEmbedding, String phase, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                select id, content, topic, gist
                from knowledge_chunk
                where embedding is not null
                  and (phase is null or phase = :phase)
                order by embedding <=> cast(:vec as vector)
                limit :limit
                """)
            .setParameter("phase", phase)
            .setParameter("vec", toVectorLiteral(queryEmbedding))
            .setParameter("limit", limit)
            .getResultList();

        return rows.stream()
            .map(r -> new RetrievedChunk((UUID) r[0], (String) r[1], (String) r[2], (String) r[3]))
            .toList();
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    public record RetrievedChunk(UUID id, String content, String topic, String gist) { }
}
