package com.loadedvj.backend.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void packsShortParagraphsIntoOneChunk() {
        String text = "First paragraph about jump training.\n\nSecond paragraph, still short.";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("First paragraph").contains("Second paragraph");
    }

    @Test
    void splitsProseOnceItExceedsTheTargetSize() {
        String longParagraph = "word ".repeat(250).trim();
        String text = longParagraph + "\n\n" + longParagraph;

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(2);
    }

    @Test
    void keepsATableIntactAsItsOwnChunkInsteadOfMergingWithSurroundingProse() {
        String table = """
            | Week | Exercise | Sets | Reps |
            |------|----------|------|------|
            | 1 | Back Squat | 4 | 5 |
            | 1 | Box Jump | 3 | 5 |
            """.trim();
        String text = "Intro paragraph before the table.\n\n" + table + "\n\nOutro paragraph after the table.";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).contains("Intro paragraph");
        assertThat(chunks.get(1)).contains("Back Squat").contains("Box Jump").contains("Week | Exercise");
        assertThat(chunks.get(2)).contains("Outro paragraph");
    }

    @Test
    void splitsALargeTableByRowGroupWithoutBreakingAnyRowAndKeepsTheHeaderInEachGroup() {
        StringBuilder table = new StringBuilder("| Week | Exercise | Sets | Reps |\n|------|------|------|------|\n");
        for (int i = 1; i <= 45; i++) {
            table.append("| ").append(i).append(" | Back Squat | 4 | 5 |\n");
        }

        List<String> chunks = service.chunk(table.toString().trim());

        assertThat(chunks).hasSize(3);
        assertThat(dataRowCount(chunks.get(0))).isEqualTo(20);
        assertThat(dataRowCount(chunks.get(1))).isEqualTo(20);
        assertThat(dataRowCount(chunks.get(2))).isEqualTo(5);
        for (String chunk : chunks) {
            assertThat(chunk).startsWith("| Week | Exercise | Sets | Reps |");
        }
    }

    private static long dataRowCount(String chunk) {
        return chunk.lines().skip(2).count();
    }
}
