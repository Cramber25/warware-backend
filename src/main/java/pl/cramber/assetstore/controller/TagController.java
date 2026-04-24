package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.Tag;
import pl.cramber.assetstore.repository.TagRepository;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagRepository tagRepository;

    @GetMapping
    public List<Tag> getAll() {
        return tagRepository.findAll();
    }
}