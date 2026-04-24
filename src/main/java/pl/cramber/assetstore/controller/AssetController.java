package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    @GetMapping
    public List<Asset> getPublicAssets() {
        return assetRepository.findAllByVisibility("PUBLIC");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Asset> getAssetById(@PathVariable UUID id) {
        return assetRepository.findById(id)
                .filter(asset -> !asset.getVisibility().equals("PRIVATE")) // Przepuszcza PUBLIC i UNLISTED
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}