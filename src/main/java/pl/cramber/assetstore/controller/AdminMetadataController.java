package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.Category;
import pl.cramber.assetstore.entity.Tag;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.CategoryRepository;
import pl.cramber.assetstore.repository.TagRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/metadata")
@RequiredArgsConstructor
public class AdminMetadataController {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final AssetRepository assetRepository;

    @PostMapping("/categories")
    public Category addCategory(@RequestBody Category category) {
        return categoryRepository.save(category);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable UUID id, @RequestBody Category data) {
        Category category = categoryRepository.findById(id).orElseThrow();
        category.setName(data.getName());
        return categoryRepository.save(category);
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public void deleteCategory(@PathVariable UUID id) {
        List<Asset> assets = assetRepository.findAll();
        assets.stream()
                .filter(a -> a.getCategory() != null && a.getCategory().getId().equals(id))
                .forEach(a -> a.setCategory(null));
        assetRepository.saveAll(assets);
        categoryRepository.deleteById(id);
    }

    @PostMapping("/tags")
    public Tag addTag(@RequestBody Tag tag) {
        return tagRepository.save(tag);
    }

    @PutMapping("/tags/{id}")
    public Tag updateTag(@PathVariable UUID id, @RequestBody Tag data) {
        Tag tag = tagRepository.findById(id).orElseThrow();
        tag.setName(data.getName());
        return tagRepository.save(tag);
    }

    @DeleteMapping("/tags/{id}")
    @Transactional
    public void deleteTag(@PathVariable UUID id) {
        Tag tag = tagRepository.findById(id).orElseThrow();
        List<Asset> assets = assetRepository.findAll();
        assets.forEach(a -> a.getTags().remove(tag));
        assetRepository.saveAll(assets);
        tagRepository.delete(tag);
    }
}