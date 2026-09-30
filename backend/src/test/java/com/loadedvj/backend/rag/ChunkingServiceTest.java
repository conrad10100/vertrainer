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

    @Test
    void transcriptChunkingKeepsRelatedSentencesTogetherBelowTheMinimum() {
        String squatTopic = "Back squat depth matters for jump transfer. Hit parallel or below on every rep. "
            + "Depth builds the stretch reflex you need for a real jump. Don't cut squats short in season.";

        List<String> chunks = service.chunkTranscript(squatTopic);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Back squat depth").contains("cut squats short");
    }

    @Test
    void transcriptChunkingSplitsOnATopicShiftOnceThePastMinimumSize() {
        String squatTopic = ("Back squat depth matters for jump transfer. Hit parallel or below on every rep. "
            + "Depth builds the stretch reflex you need for a real jump. Don't cut squats short in season. "
            + "Progressive overload on the squat drives long term vertical gains over many training blocks. ")
            .repeat(3);
        String nutritionTopic = "Now let's talk about protein intake for recovery. Eat about one gram per pound "
            + "of bodyweight daily. Recovery nutrition timing around lifting sessions also matters a lot for muscle repair.";

        List<String> chunks = service.chunkTranscript(squatTopic + " " + nutritionTopic);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks).anyMatch(c -> c.contains("Back squat depth"));
        assertThat(chunks).anyMatch(c -> c.contains("Eat about one gram"));
        // The topic-shift chunk (or its neighbor via overlap) should carry the nutrition content,
        // not have it swallowed into a squat-heavy chunk.
        assertThat(chunks.stream().anyMatch(c -> c.contains("Recovery nutrition timing"))).isTrue();
    }

    @Test
    void transcriptChunkingOverlapsOneSentenceAcrossABoundary() {
        String longTranscript = ("Back squat depth matters for jump transfer. Hit parallel or below on every rep. "
            + "Depth builds the stretch reflex you need for a real jump. Don't cut squats short in season. "
            + "Progressive overload on the squat drives long term vertical gains over many training blocks. ")
            .repeat(3)
            + "Now let's talk about protein intake for recovery. Eat about one gram per pound "
            + "of bodyweight daily. Recovery nutrition timing around lifting sessions also matters a lot for muscle repair.";

        List<String> chunks = service.chunkTranscript(longTranscript);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        // Every boundary keeps one sentence of overlap, so consecutive chunks share content: the
        // last sentence of chunk N should also open chunk N+1.
        for (int i = 0; i < chunks.size() - 1; i++) {
            String[] sentences = chunks.get(i).split("(?<=[.!?])\\s+");
            String lastSentence = sentences[sentences.length - 1];
            assertThat(chunks.get(i + 1)).startsWith(lastSentence);
        }
    }
}
