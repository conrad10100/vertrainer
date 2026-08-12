package com.loadedvj.backend.anthropic;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptTemplateService {

    private final PromptTemplateRepository repository;

    public PromptTemplateService(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads exactly the given template keys in one query. Always reads fresh -- no in-memory
     * caching -- so an edit made directly in the database takes effect on the very next call,
     * with no redeploy or restart needed.
     */
    public PromptSet load(String... keys) {
        Map<String, PromptTemplate> byKey = repository.findAllById(List.of(keys)).stream()
            .collect(Collectors.toMap(PromptTemplate::getKey, t -> t));
        for (String key : keys) {
            if (!byKey.containsKey(key)) {
                throw new IllegalStateException("Missing prompt template: " + key);
            }
        }
        return new PromptSet(byKey);
    }

    public static final class PromptSet {
        private final Map<String, PromptTemplate> templates;

        private PromptSet(Map<String, PromptTemplate> templates) {
            this.templates = templates;
        }

        public String get(String key) {
            return templates.get(key).getContent();
        }

        /**
         * ISO-8601 timestamp of the most recently edited template in this set -- used as the
         * audit log's prompt_version, so every call is attributable to the exact database content
         * that produced it without anyone having to remember to hand-bump a version string.
         */
        public String version() {
            Instant latest = templates.values().stream()
                .map(PromptTemplate::getUpdatedAt)
                .max(Instant::compareTo)
                .orElseThrow();
            return DateTimeFormatter.ISO_INSTANT.format(latest);
        }
    }
}
