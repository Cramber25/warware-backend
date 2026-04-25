package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SeoController {

    private final AssetRepository assetRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping(value = "/asset/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getAssetPage(@PathVariable String id) {
        String frontendUrl = "http://roblox_store_frontend:80/index.html";
        String html;

        try {
            html = restTemplate.getForObject(frontendUrl, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error loading frontend index.html");
        }

        if (html == null) {
            return ResponseEntity.status(500).body("Frontend returned empty response");
        }

        UUID assetId;
        try {
            assetId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(html);
        }

        Asset asset = assetRepository.findById(assetId).orElse(null);

        if (asset == null || "ARCHIVED".equals(asset.getVisibility()) || "PRIVATE".equals(asset.getVisibility())) {
            return ResponseEntity.ok(html);
        }

        String title = asset.getTitle() + " | WarWare Store";
        String description = cleanMarkdown(asset.getDescription());
        if (description.length() > 150) {
            description = description.substring(0, 147) + "...";
        }
        String image = asset.getThumbnailUrl();

        return ResponseEntity.ok(injectMetaTags(html, title, description, image));
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("[#*_\\->\\[\\]()]", "").trim();
    }

    private String injectMetaTags(String html, String title, String description, String image) {
        return html
                .replaceAll("<title>.*?</title>", "<title>" + title + "</title>")

                .replaceAll("<meta property=\"og:title\" content=\".*?\">", "<meta property=\"og:title\" content=\"" + title + "\">")
                .replaceAll("<meta property=\"og:description\" content=\".*?\">", "<meta property=\"og:description\" content=\"" + description + "\">")
                .replaceAll("<meta property=\"og:image\" content=\".*?\">", "<meta property=\"og:image\" content=\"" + image + "\">")

                .replaceAll("<meta name=\"twitter:title\" content=\".*?\">", "<meta name=\"twitter:title\" content=\"" + title + "\">")
                .replaceAll("<meta name=\"twitter:description\" content=\".*?\">", "<meta name=\"twitter:description\" content=\"" + description + "\">")
                .replaceAll("<meta name=\"twitter:image\" content=\".*?\">", "<meta name=\"twitter:image\" content=\"" + image + "\">");
    }
}