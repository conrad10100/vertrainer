package com.loadedvj.backend.anthropic;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {
}
