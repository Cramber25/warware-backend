package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.PenaltyDto;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.PenaltyService;

import java.util.List;

@RestController
@RequestMapping("/api/penalties")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class PenaltyController {

    private final PenaltyService penaltyService;
    private final UserRepository userRepository;

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PenaltyDto>> getMyPenalties(@AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        List<PenaltyDto> penalties = penaltyService.getUserPenalties(user);
        return ResponseEntity.ok(penalties);
    }
}