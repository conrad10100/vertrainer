package com.loadedvj.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps Voyage AI's embeddings endpoint (Anthropic's recommended embedding partner -- Claude
 * itself doesn't serve embeddings). voyage-3 produces 1024-dim vectors, matching the
 * knowledge_chunk.embedding column width in schema.sql; changing models means re-embedding every
 * existing chunk.
 */
@Service
public class EmbeddingService {

    private static final String ENDPOINT = "https://api.voyageai.com/v1/embeddings";
    private static final String MODEL = "voyage-3";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingService(@Value("${voyage.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** input_type "document" -- for text being stored/indexed. */
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "document");
    }

    /** input_type "query" -- for the text a search is being run against; Voyage's models are
     * trained asymmetrically, so a query should not be embedded the same way as a document. */
    public float[] embedQuery(String text) {
        return embed(List.of(text), "query").get(0);
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        body.put("model", MODEL);
        body.put("input_type", inputType);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new KnowledgeIngestionException(
                    "Voyage embeddings call failed with HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<float[]> result = new ArrayList<>();
            for (JsonNode item : root.get("data")) {
                JsonNode vec = item.get("embedding");
                float[] embedding = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    embedding[i] = vec.get(i).floatValue();
                }
                result.add(embedding);
            }
            return result;
        } catch (IOException e) {
            throw new KnowledgeIngestionException("Voyage embeddings call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnowledgeIngestionException("Voyage embeddings call interrupted", e);
        }
    }
}
