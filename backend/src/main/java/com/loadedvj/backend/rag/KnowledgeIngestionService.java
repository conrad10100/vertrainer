package com.loadedvj.backend.rag;

import com.loadedvj.backend.rag.KnowledgeDtos.ChunkSummary;
import com.loadedvj.backend.rag.KnowledgeDtos.IngestResult;
import com.loadedvj.backend.rag.KnowledgeModels.ChunkTag;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates one ingestion end to end: extract -> normalize -> chunk -> tag -> embed -> save.
 * Used by the admin endpoint for all three input kinds (PDF/text file, YouTube URL, pasted text).
 */
@Service
public class KnowledgeIngestionService {

    private final TranscriptExtractionService extractionService;
    private final MarkdownNormalizationService normalizationService;
    private final ChunkingService chunkingService;
    private final KnowledgeTaggingService taggingService;
    private final EmbeddingService embeddingService;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeVectorRepository vectorRepository;

    public KnowledgeIngestionService(TranscriptExtractionService extractionService,
                                      MarkdownNormalizationService normalizationService,
                                      ChunkingService chunkingService,
                                      KnowledgeTaggingService taggingService,
                                      EmbeddingService embeddingService,
                                      KnowledgeSourceRepository sourceRepository,
                                      KnowledgeChunkRepository chunkRepository,
                                      KnowledgeVectorRepository vectorRepository) {
        this.extractionService = extractionService;
        this.normalizationService = normalizationService;
        this.chunkingService = chunkingService;
        this.taggingService = taggingService;
        this.embeddingService = embeddingService;
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.vectorRepository = vectorRepository;
    }

    public IngestResult ingestPdf(byte[] pdfBytes, String filename) {
        return ingest("pdf", filename, filename, extractionService.extractFromPdf(pdfBytes));
    }

    public IngestResult ingestYoutube(String youtubeUrl) {
        return ingest("youtube", youtubeUrl, youtubeUrl, extractionService.extractFromYoutube(youtubeUrl));
    }

    public IngestResult ingestText(String title, String text) {
        return ingest("text", title, title, text);
    }

    private IngestResult ingest(String sourceType, String origin, String title, String rawText) {
        String normalized = normalizationService.normalize(rawText);
        List<String> chunkTexts = "youtube".equals(sourceType)
            ? chunkingService.chunkTranscript(normalized)
            : chunkingService.chunk(normalized);
        if (chunkTexts.isEmpty()) {
            throw new KnowledgeIngestionException("Nothing to ingest -- extracted text was empty after normalization");
        }

        KnowledgeSource source = new KnowledgeSource();
        source.setSourceType(sourceType);
        source.setOrigin(origin);
        source.setTitle(title);
        source = sourceRepository.save(source);

        List<ChunkTag> tags = chunkTexts.stream().map(taggingService::tag).toList();
        List<float[]> embeddings = embeddingService.embedDocuments(chunkTexts);

        List<ChunkSummary> summaries = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            ChunkTag tag = tags.get(i);

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setSourceId(source.getId());
            chunk.setContent(chunkTexts.get(i));
            chunk.setPhase(tag.phase());
            chunk.setTopic(tag.topic());
            chunk.setGist(tag.gist());
            chunk = chunkRepository.save(chunk);

            vectorRepository.setEmbedding(chunk.getId(), embeddings.get(i));
            summaries.add(new ChunkSummary(chunk.getId(), tag.phase(), tag.topic(), tag.gist()));
        }

        return new IngestResult(source.getId(), summaries.size(), summaries);
    }
}
