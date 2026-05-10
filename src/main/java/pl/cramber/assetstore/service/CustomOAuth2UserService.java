package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final R2Service r2Service;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String discordId = oAuth2User.getAttribute("id");
        String username = oAuth2User.getAttribute("username");
        String avatarHash = oAuth2User.getAttribute("avatar");
        String email = oAuth2User.getAttribute("email");

        String sourceAvatarUrl = avatarHash != null
                ? "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".webp?size=128"
                : "https://cdn.discordapp.com/embed/avatars/0.png";

        String finalAvatarUrl = r2Service.uploadAvatarFromUrl(sourceAvatarUrl, "discord", discordId);

        Optional<User> existingUserOpt = userRepository.findByDiscordId(discordId);

        User user = existingUserOpt
                .map(existingUser -> {
                    existingUser.setDiscordUsername(username);
                    existingUser.setDiscordAvatarUrl(finalAvatarUrl);
                    existingUser.setEmail(email);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .discordId(discordId)
                            .discordUsername(username)
                            .discordAvatarUrl(finalAvatarUrl)
                            .email(email)
                            .build();
                    return userRepository.save(newUser);
                });

        List<GrantedAuthority> authorities = new ArrayList<>(oAuth2User.getAuthorities());

        if (user.isBanned()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_BANNED"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        }

        return new DefaultOAuth2User(
                authorities,
                oAuth2User.getAttributes(),
                "id"
        );
    }
}