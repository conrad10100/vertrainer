package com.loadedvj.backend.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.loadedvj.backend.rag.KnowledgeModels.NormalizedDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cleans up raw extracted text (from a PDF or a YouTube transcript) before chunking -- mainly so
 * tabular data (a workout plan, a periodization table) survives as a real markdown table instead
 * of getting mangled by whatever text-extraction artifacts PDFBox or caption text introduces.
 * Uses the same Claude structured-output mechanism as ProgramGenerationService, just for a
 * text-cleanup shape instead of a program-generation one.
 */
@Service
public class MarkdownNormalizationService {

    private final AnthropicClient client;
    private final String model;

    public MarkdownNormalizationService(AnthropicClient client, @Value("${anthropic.model}") String model) {
        this.client = client;
        this.model = model;
    }

    public String normalize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        StructuredMessageCreateParams<NormalizedDocument> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .outputConfig(NormalizedDocument.class)
            .addUserMessage(rawText)
            .build();

        StructuredMessage<NormalizedDocument> response = client.messages().create(params);
        return response.content().stream()
            .flatMap(b -> b.text().stream())
            .findFirst()
            .map(StructuredTextBlock::text)
            .map(NormalizedDocument::markdown)
            .orElseThrow(() -> new KnowledgeIngestionException("Claude returned no structured content for normalization"));
    }
}
