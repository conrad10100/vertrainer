package com.loadedvj.backend.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.loadedvj.backend.rag.KnowledgeModels.ChunkTag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Auto-tags one ingested chunk (phase, topic, gist) so it doesn't need hand-tagging on every
 * ingest -- you review the gists in the ingestion response instead of writing tags from scratch.
 */
@Service
public class KnowledgeTaggingService {

    private static final String SYSTEM_PROMPT = """
        You tag passages of vertical-jump training knowledge (excerpts from studies and coaching \
        videos) for a retrieval system used when generating a training program. Read the passage \
        and assign a training phase (if it's specific to one), a short topic, and a one-sentence gist \
        describing what it covers -- the gist is what a semantic search matches against, so make it \
        descriptive even when the passage itself is terse (e.g. a workout table with little prose).""";

    private final AnthropicClient client;
    private final String model;

    public KnowledgeTaggingService(AnthropicClient client, @Value("${anthropic.model}") String model) {
        this.client = client;
        this.model = model;
    }

    public ChunkTag tag(String chunkContent) {
        StructuredMessageCreateParams<ChunkTag> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(500L)
            .system(SYSTEM_PROMPT)
            .outputConfig(ChunkTag.class)
            .addUserMessage(chunkContent)
            .build();

        StructuredMessage<ChunkTag> response = client.messages().create(params);
        return response.content().stream()
            .flatMap(b -> b.text().stream())
            .findFirst()
            .map(StructuredTextBlock::text)
            .orElseThrow(() -> new KnowledgeIngestionException("Claude returned no structured content for tagging"));
    }
}
