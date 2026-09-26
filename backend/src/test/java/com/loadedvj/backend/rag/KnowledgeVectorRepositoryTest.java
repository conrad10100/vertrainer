package com.loadedvj.backend.rag;

import com.loadedvj.backend.rag.KnowledgeVectorRepository.RetrievedChunk;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real pgvector column and its cosine-distance (<=>) operator against an actual
 * Postgres -- H2 (used by the rest of the test suite, see test/resources/application.properties)
 * has no vector type or operator, so this repository is the one thing that needs the real thing.
 * ddl-auto=create-drop still generates every other entity's table the normal way; only the
 * embedding column (unmapped in the KnowledgeChunk entity -- see its class comment) and the
 * extension are added by hand in setUp(), matching what schema.sql does in production.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class KnowledgeVectorRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KnowledgeVectorRepository vectorRepository;
    @Autowired
    private KnowledgeSourceRepository sourceRepository;
    @Autowired
    private KnowledgeChunkRepository chunkRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("create extension if not exists vector").executeUpdate();
        entityManager.createNativeQuery(
            "alter table knowledge_chunk add column if not exists embedding vector(3)").executeUpdate();
    }

    @AfterEach
    void tearDown() {
        chunkRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    @Test
    void returnsNearestChunksFirstByCosineDistance() {
        UUID close = saveChunk("Accumulation", new float[] {1f, 0f, 0f});
        UUID far = saveChunk("Accumulation", new float[] {0f, 1f, 0f});

        List<RetrievedChunk> results = vectorRepository.search(new float[] {0.9f, 0.1f, 0f}, "Accumulation", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(close);
        assertThat(results.get(1).id()).isEqualTo(far);
    }

    @Test
    void includesPhaseAgnosticChunksButExcludesOtherPhases() {
        UUID accumulation = saveChunk("Accumulation", new float[] {1f, 0f, 0f});
        UUID general = saveChunk(null, new float[] {1f, 0f, 0f});
        saveChunk("Realization", new float[] {1f, 0f, 0f});

        List<RetrievedChunk> results = vectorRepository.search(new float[] {1f, 0f, 0f}, "Accumulation", 10);

        assertThat(results).extracting(RetrievedChunk::id).containsExactlyInAnyOrder(accumulation, general);
    }

    @Test
    void excludesChunksWithNoEmbeddingYet() {
        KnowledgeSource source = sourceRepository.save(newSource());
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSourceId(source.getId());
        chunk.setContent("not yet embedded");
        chunkRepository.save(chunk);

        List<RetrievedChunk> results = vectorRepository.search(new float[] {1f, 0f, 0f}, "Accumulation", 10);

        assertThat(results).isEmpty();
    }

    private UUID saveChunk(String phase, float[] embedding) {
        KnowledgeSource source = sourceRepository.save(newSource());

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSourceId(source.getId());
        chunk.setContent("some training knowledge");
        chunk.setPhase(phase);
        chunk.setTopic("plyometrics");
        chunk.setGist("a gist");
        chunk = chunkRepository.save(chunk);

        vectorRepository.setEmbedding(chunk.getId(), embedding);
        return chunk.getId();
    }

    private KnowledgeSource newSource() {
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceType("text");
        source.setOrigin("test");
        source.setTitle("test source");
        return source;
    }
}
