package com.loadedvj.backend.rag;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Shapes returned by Claude via structured outputs during ingestion -- same mechanism
 * GenerationModels uses for program generation (the JSON schema is derived from the record, so
 * the response deserializes straight into it).
 */
public final class KnowledgeModels {

    private KnowledgeModels() { }

    public record NormalizedDocument(
        @JsonPropertyDescription("""
            The input text, cleaned up for a knowledge base: reconstruct any tabular data (workout \
            plans, rep/set schemes, periodization tables) as proper markdown tables (a header row, a \
            separator row, then data rows), fix obvious extraction artifacts (broken line wraps, \
            repeated headers/footers, filler speech in a transcript) -- but do not summarize, shorten, \
            paraphrase, or add commentary. Prose paragraphs stay as plain text, wording unchanged.""")
        String markdown
    ) { }

    public record ChunkTag(
        @JsonPropertyDescription("""
            One of "Accumulation" (general strength & work capacity, higher volume), "Intensification" \
            (maximal strength, heavier loads/lower reps), or "Realization" (power & reactive strength, \
            plyometrics) if this chunk's content is specific to that training phase, or null if it's \
            general/phase-agnostic knowledge (applies regardless of phase). Deload is not a phase of its \
            own here -- it's a lighter week within any of the three phases, so deload-specific guidance \
            (e.g. how to reduce volume) should be tagged null unless it's specific to one phase.""")
        String phase,
        @JsonPropertyDescription("Short free-text topic, e.g. \"plyometrics\", \"RFD\", \"eccentric strength\"")
        String topic,
        @JsonPropertyDescription(
            "One sentence describing what this chunk covers -- this is what retrieval matches against, "
            + "so make it descriptive of the content even when the chunk itself is terse (e.g. a table).")
        String gist
    ) { }
}
