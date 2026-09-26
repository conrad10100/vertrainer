package com.loadedvj.backend.web;

import com.loadedvj.backend.rag.KnowledgeDtos.IngestResult;
import com.loadedvj.backend.rag.KnowledgeDtos.IngestTextRequest;
import com.loadedvj.backend.rag.KnowledgeDtos.IngestYoutubeRequest;
import com.loadedvj.backend.rag.KnowledgeIngestionService;
import com.loadedvj.backend.service.AdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Admin-only ingestion endpoint for the vertical-jump training knowledge base: a PDF/text file,
 * a YouTube URL, or pasted text (for a video with no captions), all funneled into the same
 * extract -> normalize -> chunk -> tag -> embed pipeline (KnowledgeIngestionService).
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {

    private final AdminService adminService;
    private final KnowledgeIngestionService ingestionService;

    public KnowledgeAdminController(AdminService adminService, KnowledgeIngestionService ingestionService) {
        this.adminService = adminService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/file")
    public IngestResult ingestFile(@AuthenticationPrincipal Jwt jwt, @RequestPart MultipartFile file) {
        adminService.requireAdmin(jwt);
        try {
            String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            if (filename.toLowerCase().endsWith(".pdf")) {
                return ingestionService.ingestPdf(file.getBytes(), filename);
            }
            return ingestionService.ingestText(filename, new String(file.getBytes()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @PostMapping("/youtube")
    public IngestResult ingestYoutube(@AuthenticationPrincipal Jwt jwt, @RequestBody IngestYoutubeRequest request) {
        adminService.requireAdmin(jwt);
        return ingestionService.ingestYoutube(request.youtubeUrl());
    }

    @PostMapping("/text")
    public IngestResult ingestText(@AuthenticationPrincipal Jwt jwt, @RequestBody IngestTextRequest request) {
        adminService.requireAdmin(jwt);
        return ingestionService.ingestText(request.title(), request.text());
    }
}
