package pl.cramber.assetstore.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class SeoController {

    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private String cachedHtml = null;
    private long cacheLastFetched = 0;
    private static final long CACHE_DURATION_MS = 60 * 1000 * 5;

    @GetMapping(value = "/asset/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getAssetPage(@PathVariable String id) {
        String html = getFrontendHtml();

        if (html == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Frontend returned empty response");
        }

        UUID assetId;
        try {
            assetId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(html);
        }

        Asset asset = assetRepository.findById(assetId).orElse(null);

        if (asset == null || "ARCHIVED".equals(asset.getVisibility()) || "PRIVATE".equals(asset.getVisibility())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(html);
        }

        String description = cleanMarkdown(asset.getDescription());
        if (description.length() > 150) {
            description = description.substring(0, 147) + "...";
        }

        String safeTitle = HtmlUtils.htmlEscape(asset.getTitle());
        String safeDescription = HtmlUtils.htmlEscape(description);
        String safeImage = HtmlUtils.htmlEscape(asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : "");

        String jsonLd = buildProductJsonLd(asset, safeDescription);

        return ResponseEntity.ok(injectMetaTags(html, safeTitle, safeDescription, safeImage, jsonLd));
    }

    private String getFrontendHtml() {
        if (cachedHtml != null && (System.currentTimeMillis() - cacheLastFetched) < CACHE_DURATION_MS) {
            return cachedHtml;
        }

        try {
            String frontendUrl = "http://roblox_store_frontend:80/index.html";
            cachedHtml = restTemplate.getForObject(frontendUrl, String.class);
            cacheLastFetched = System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Error loading frontend index.html", e);
        }
        return cachedHtml;
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("[#*_>\\[\\]]", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildProductJsonLd(Asset asset, String cleanDescription) {
        Map<String, Object> jsonLd = new HashMap<>();
        jsonLd.put("@context", "https://schema.org/");
        jsonLd.put("@type", "Product");
        jsonLd.put("name", asset.getTitle());
        jsonLd.put("image", asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : "");
        jsonLd.put("description", cleanDescription + " | Price: " + asset.getPrice() + " Robux.");

        Map<String, Object> offer = new HashMap<>();
        offer.put("@type", "Offer");
        offer.put("price", asset.getPrice());
        offer.put("priceCurrency", "USD");
        offer.put("availability", "https://schema.org/InStock");
        jsonLd.put("offers", offer);

        if (asset.getRatingCount() != null && asset.getRatingCount() > 0) {
            Map<String, Object> rating = new HashMap<>();
            rating.put("@type", "AggregateRating");
            rating.put("ratingValue", Math.round(asset.getAverageRating() * 10.0) / 10.0);
            rating.put("reviewCount", asset.getRatingCount());
            jsonLd.put("aggregateRating", rating);
        }

        try {
            return objectMapper.writeValueAsString(jsonLd);
        } catch (JsonProcessingException e) {
            log.error("Failed to generate JSON-LD", e);
            return "{}";
        }
    }

    private String injectMetaTags(String html, String title, String description, String image, String jsonLd) {
        String jsonLdScript = "<script type=\"application/ld+json\">\n" + jsonLd + "\n</script>";

        StringBuilder seoTags = new StringBuilder();
        seoTags.append("<title>").append(title).append("</title>\n");
        seoTags.append("<meta name=\"title\" content=\"").append(title).append("\" />\n");
        seoTags.append("<meta name=\"description\" content=\"").append(description).append("\" />\n");

        seoTags.append("<meta property=\"og:title\" content=\"").append(title).append("\" />\n");
        seoTags.append("<meta property=\"og:description\" content=\"").append(description).append("\" />\n");
        seoTags.append("<meta property=\"og:image\" content=\"").append(image).append("\" />\n");
        seoTags.append("<meta property=\"og:type\" content=\"product\" />\n");

        seoTags.append("<meta property=\"twitter:card\" content=\"summary_large_image\" />\n");
        seoTags.append("<meta property=\"twitter:title\" content=\"").append(title).append("\" />\n");
        seoTags.append("<meta property=\"twitter:description\" content=\"").append(description).append("\" />\n");
        seoTags.append("<meta property=\"twitter:image\" content=\"").append(image).append("\" />\n");

        seoTags.append(jsonLdScript).append("\n");

        String cleanedHtml = html.replaceAll("<title>.*?</title>", "");

        cleanedHtml = cleanedHtml.replaceAll("<meta[^>]+(?:name|property)=[\"'](?:title|description|og:[^\"']+|twitter:[^\"']+)[\"'][^>]*>", "");

        return cleanedHtml.replaceFirst("</head>", Matcher.quoteReplacement(seoTags.toString() + "</head>"));
    }
}