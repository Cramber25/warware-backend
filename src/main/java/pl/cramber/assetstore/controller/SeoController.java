package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;

import java.util.UUID;
import java.util.regex.Matcher;

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

        String description = cleanMarkdown(asset.getDescription());
        if (description.length() > 150) {
            description = description.substring(0, 147) + "...";
        }

        String safeTitle = HtmlUtils.htmlEscape(asset.getTitle());
        String safeDescription = HtmlUtils.htmlEscape(description);
        String safeImage = HtmlUtils.htmlEscape(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : "");

        return ResponseEntity.ok(injectMetaTags(html, safeTitle, safeDescription, safeImage));
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("[#*_\\->\\[\\]()]", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String injectMetaTags(String html, String title, String description, String image) {
        return html
                .replaceAll("<title>.*?</title>", Matcher.quoteReplacement("<title>" + title + "</title>"))

                .replaceAll("<meta\\s+name=\"title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta name=\"title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+name=\"description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta name=\"description\" content=\"" + description + "\" />"))

                .replaceAll("<meta\\s+property=\"og:title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+property=\"og:description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:description\" content=\"" + description + "\" />"))
                .replaceAll("<meta\\s+property=\"og:image\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:image\" content=\"" + image + "\" />"))

                .replaceAll("<meta\\s+property=\"twitter:title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+property=\"twitter:description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:description\" content=\"" + description + "\" />"))
                .replaceAll("<meta\\s+property=\"twitter:image\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:image\" content=\"" + image + "\" />"));
    }
}