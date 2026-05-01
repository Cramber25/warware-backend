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

        String jsonLd = buildProductJsonLd(asset, safeDescription);

        return ResponseEntity.ok(injectMetaTags(html, safeTitle, safeDescription, safeImage, jsonLd));
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("[#*_\\->\\[\\]()]", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildProductJsonLd(Asset asset, String cleanDescription) {
        String jsonTitle = asset.getTitle().replace("\"", "\\\"");
        String priceString = String.valueOf(asset.getPrice());
        String jsonImage = asset.getThumbnailUrl() != null ? asset.getThumbnailUrl() : "";

        String enhancedDescription = cleanDescription + " | Price: " + priceString + " Robux.";
        String jsonDesc = enhancedDescription.replace("\"", "\\\"");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"@context\": \"https://schema.org/\",\n");
        sb.append("  \"@type\": \"Product\",\n");
        sb.append("  \"name\": \"").append(jsonTitle).append("\",\n");
        sb.append("  \"image\": \"").append(jsonImage).append("\",\n");
        sb.append("  \"description\": \"").append(jsonDesc).append("\",\n");
        sb.append("  \"offers\": {\n");
        sb.append("    \"@type\": \"Offer\",\n");
        sb.append("    \"price\": \"").append(priceString).append("\",\n");
        sb.append("    \"availability\": \"https://schema.org/InStock\"\n");
        sb.append("  }");

        if (asset.getRatingCount() != null && asset.getRatingCount() > 0) {
            sb.append(",\n  \"aggregateRating\": {\n");
            sb.append("    \"@type\": \"AggregateRating\",\n");
            sb.append("    \"ratingValue\": \"").append(Math.round(asset.getAverageRating() * 10.0) / 10.0).append("\",\n");
            sb.append("    \"reviewCount\": \"").append(asset.getRatingCount()).append("\"\n");
            sb.append("  }\n");
        } else {
            sb.append("\n");
        }

        sb.append("}");

        return sb.toString();
    }

    private String injectMetaTags(String html, String title, String description, String image, String jsonLd) {

        String jsonLdScript = "<script type=\"application/ld+json\">\n" + jsonLd + "\n</script>";

        String injectedHtml = html
                .replaceAll("<title>.*?</title>", Matcher.quoteReplacement("<title>" + title + "</title>"))

                .replaceAll("<meta\\s+name=\"title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta name=\"title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+name=\"description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta name=\"description\" content=\"" + description + "\" />"))

                .replaceAll("<meta\\s+property=\"og:title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+property=\"og:description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:description\" content=\"" + description + "\" />"))
                .replaceAll("<meta\\s+property=\"og:image\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"og:image\" content=\"" + image + "\" />"))

                .replaceAll("<meta\\s+property=\"twitter:title\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:title\" content=\"" + title + "\" />"))
                .replaceAll("<meta\\s+property=\"twitter:description\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:description\" content=\"" + description + "\" />"))
                .replaceAll("<meta\\s+property=\"twitter:image\"\\s+content=\".*?\"\\s*/?>", Matcher.quoteReplacement("<meta property=\"twitter:image\" content=\"" + image + "\" />"));

        return injectedHtml.replaceFirst("</head>", Matcher.quoteReplacement(jsonLdScript + "\n</head>"));
    }
}