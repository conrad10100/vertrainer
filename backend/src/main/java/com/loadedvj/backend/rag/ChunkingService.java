package com.loadedvj.backend.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }
}
