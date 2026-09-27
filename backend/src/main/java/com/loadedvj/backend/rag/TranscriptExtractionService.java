package com.loadedvj.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw ingestion input (PDF bytes, a YouTube URL, or already-plain text) into extracted
 * text, before MarkdownNormalizationService cleans it up and ChunkingService splits it. No API
 * key needed for the YouTube path -- caption tracks are fetched the same way a browser does, by
 * reading the watch page's embedded player data, not via the (key-gated) YouTube Data API.
 */
@Service
public class TranscriptExtractionService {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
        "(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})");
    private static final Pattern CAPTION_TRACKS_PATTERN = Pattern.compile(
        "\"captionTracks\":(\\[.*?])");

    // A cookie jar (shared across requests on this client) plus a preemptive CONSENT cookie on
    // the watch-page request below skips YouTube's EU cookie-consent interstitial, which
    // otherwise 302-redirects every unauthenticated request in a loop that never reaches the
    // actual watch page -- HttpClient.Redirect.NORMAL alone can't get through that.
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractFromPdf(byte[] pdfBytes) {
        try (var document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new KnowledgeIngestionException("Failed to extract text from PDF", e);
        }
    }

    public String extractFromYoutube(String url) {
        String videoId = extractVideoId(url);
        String watchPageHtml = get("https://www.youtube.com/watch?v=" + videoId, "CONSENT=YES+1");
        String captionUrl = findCaptionTrackUrl(watchPageHtml);
        String captionXml = get(captionUrl, null);
        return stripCaptionMarkup(captionXml);
    }

    private String extractVideoId(String url) {
        Matcher m = VIDEO_ID_PATTERN.matcher(url);
        if (!m.find()) {
            throw new KnowledgeIngestionException("Could not parse a video id out of: " + url);
        }
        return m.group(1);
    }

    private String findCaptionTrackUrl(String watchPageHtml) {
        Matcher m = CAPTION_TRACKS_PATTERN.matcher(watchPageHtml);
        if (!m.find()) {
            throw new KnowledgeIngestionException(
                "This video has no caption tracks (manual or auto-generated) -- paste its transcript as text instead.");
        }
        try {
            JsonNode tracks = objectMapper.readTree(m.group(1));
            JsonNode chosen = null;
            for (JsonNode track : tracks) {
                String lang = track.path("languageCode").asText("");
                boolean isAsr = "asr".equals(track.path("kind").asText(""));
                if (lang.startsWith("en") && !isAsr) {
                    chosen = track;
                    break;
                }
                if (lang.startsWith("en") && chosen == null) {
                    chosen = track;
                }
            }
            if (chosen == null) {
                chosen = tracks.get(0);
            }
            String baseUrl = chosen.path("baseUrl").asText(null);
            if (baseUrl == null) {
                throw new KnowledgeIngestionException("Caption track had no baseUrl");
            }
            return baseUrl.replace("\\u0026", "&");
        } catch (IOException e) {
            throw new KnowledgeIngestionException("Failed to parse caption track list", e);
        }
    }

    /** Caption XML looks like <text start="1.2" dur="3.4">some words</text> per line -- keep just the words. */
    private String stripCaptionMarkup(String captionXml) {
        return captionXml
            .replaceAll("<text[^>]*>", "\n")
            .replaceAll("</text>", "")
            .replaceAll("<[^>]+>", "")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .lines()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .reduce((a, b) -> a + " " + b)
            .orElse("");
    }

    private String get(String url, String cookie) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "en-US,en;q=0.9");
            if (cookie != null) {
                builder.header("Cookie", cookie);
            }
            HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String location = response.headers().firstValue("Location").orElse("none");
                throw new KnowledgeIngestionException(
                    "Request to " + url + " returned HTTP " + response.statusCode() + " (Location: " + location + ")");
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new KnowledgeIngestionException("Request to " + url + " failed", e);
        }
    }
}
