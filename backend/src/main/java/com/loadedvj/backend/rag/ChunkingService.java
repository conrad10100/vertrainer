package com.loadedvj.backend.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits ingested, markdown-normalized text into retrievable passages. Two kinds of block get
 * different treatment: a markdown table is never split mid-row (each split keeps the header +
 * separator row so it reads standalone), while prose paragraphs get packed together up to a
 * target size. Table rows carry little narrative language for an embedding to latch onto -- that
 * gap is filled by KnowledgeTaggingService's gist, not by this class.
 */
@Service
public class ChunkingService {

    private static final int TARGET_WORDS = 300;
    private static final int MAX_TABLE_ROWS_PER_CHUNK = 20;

    // --- Transcript (YouTube) chunking: a sentence-level sliding window that breaks at a topic
    // shift instead of a fixed word count. Spoken transcripts rarely carry the blank-line
    // paragraph breaks the prose packer above relies on, and a coach can drift topic mid-paragraph,
    // so boundaries are picked from local lexical similarity between sentences rather than length
    // alone. Similarity is plain word-overlap (no embedding calls) so this stays free to run.
    static final int TRANSCRIPT_MIN_WORDS = 150;
    static final int TRANSCRIPT_MAX_WORDS = 400;
    private static final double SIMILARITY_THRESHOLD = 0.15;
    private static final int OVERLAP_SENTENCES = 1;
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9\"'])");
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "the", "and", "or", "but", "so", "if", "then", "than", "that", "this", "these",
        "those", "is", "are", "was", "were", "be", "been", "being", "to", "of", "in", "on", "at",
        "for", "with", "as", "by", "it", "its", "you", "your", "we", "our", "i", "they", "them",
        "he", "she", "his", "her", "not", "no", "do", "does", "did", "just", "like", "really",
        "get", "got", "going", "gonna", "know", "think", "want", "okay", "ok", "um", "uh", "yeah");

    /** Chunks a YouTube transcript via sentence-level sliding window with similarity-based
     * boundaries, keeping one sentence of overlap across a break for continuity. A low-similarity
     * sentence only triggers a break once the sentence after it confirms the shift -- otherwise a
     * single aside or transitional filler ("Okay, so...") could split a chunk that's really still
     * on-topic. */
    public List<String> chunkTranscript(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return List.of();
        }
        List<Map<String, Integer>> sentenceFreqs = sentences.stream().map(ChunkingService::wordFrequencies).toList();

        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Map<String, Integer> currentFreq = new HashMap<>();
        int currentWords = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            Map<String, Integer> sentFreq = sentenceFreqs.get(i);
            int sentWords = wordCount(sentence);

            if (!current.isEmpty() && currentWords >= TRANSCRIPT_MIN_WORDS) {
                boolean tooLarge = currentWords + sentWords > TRANSCRIPT_MAX_WORDS;
                boolean shiftHere = cosineSimilarity(currentFreq, sentFreq) < SIMILARITY_THRESHOLD;
                boolean nextConfirms = i + 1 >= sentences.size()
                    || cosineSimilarity(currentFreq, sentenceFreqs.get(i + 1)) < SIMILARITY_THRESHOLD;
                if (tooLarge || (shiftHere && nextConfirms)) {
                    chunks.add(String.join(" ", current));
                    List<String> overlap = current.subList(Math.max(0, current.size() - OVERLAP_SENTENCES), current.size());
                    current = new ArrayList<>(overlap);
                    currentFreq = new HashMap<>();
                    currentWords = 0;
                    for (String s : current) {
                        mergeFrequencies(currentFreq, wordFrequencies(s));
                        currentWords += wordCount(s);
                    }
                }
            }

            current.add(sentence);
            mergeFrequencies(currentFreq, sentFreq);
            currentWords += sentWords;
        }
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current));
        }
        return chunks;
    }

    // Package-private (not private) so ChunkingQualityEvalTest can drive the real boundary logic
    // instead of reimplementing it.
    static List<String> splitSentences(String text) {
        return Arrays.stream(SENTENCE_BOUNDARY.split(text.trim()))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    static Map<String, Integer> wordFrequencies(String sentence) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : sentence.toLowerCase().split("[^a-z0-9']+")) {
            if (token.length() < 3 || STOPWORDS.contains(token)) {
                continue;
            }
            freq.merge(token, 1, Integer::sum);
        }
        return freq;
    }

    private static void mergeFrequencies(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((word, count) -> target.merge(word, count, Integer::sum));
    }

    /** Cosine similarity over word-frequency vectors. Sentences with no scorable words (e.g. all
     * stopwords, or a lone "yeah") carry no signal either way, so they're treated as similar
     * rather than forced into a split. */
    static double cosineSimilarity(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 1.0;
        }
        Set<String> shared = new HashSet<>(a.keySet());
        shared.retainAll(b.keySet());
        double dot = shared.stream().mapToDouble(w -> a.get(w) * b.get(w)).sum();
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> (double) v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> (double) v * v).sum());
        return dot / (normA * normB);
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWords = 0;

        for (String block : splitBlocks(text)) {
            if (isMarkdownTable(block)) {
                if (currentWords > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    currentWords = 0;
                }
                chunks.addAll(splitTable(block));
                continue;
            }

            int blockWords = wordCount(block);
            if (currentWords > 0 && currentWords + blockWords > TARGET_WORDS) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                currentWords = 0;
            }
            current.append(block).append("\n\n");
            currentWords += blockWords;
        }
        if (currentWords > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private static List<String> splitBlocks(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static boolean isMarkdownTable(String block) {
        List<String> lines = block.lines().toList();
        if (lines.size() < 2) {
            return false;
        }
        long pipeLines = lines.stream().filter(l -> l.trim().startsWith("|")).count();
        return pipeLines >= 2;
    }

    /** Keeps the header + separator row with every split group, so each chunk is self-contained. */
    private static List<String> splitTable(String block) {
        List<String> lines = block.lines().toList();
        if (lines.size() <= 2 + MAX_TABLE_ROWS_PER_CHUNK) {
            return List.of(block);
        }
        String header = lines.get(0);
        String separator = lines.get(1);
        List<String> dataRows = lines.subList(2, lines.size());

        List<String> result = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i += MAX_TABLE_ROWS_PER_CHUNK) {
            List<String> group = dataRows.subList(i, Math.min(i + MAX_TABLE_ROWS_PER_CHUNK, dataRows.size()));
            StringBuilder sb = new StringBuilder();
            sb.append(header).append('\n').append(separator).append('\n');
            group.forEach(row -> sb.append(row).append('\n'));
            result.add(sb.toString().trim());
        }
        return result;
    }

    static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }
}
