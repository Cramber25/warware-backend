package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
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
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String discordId = oAuth2User.getAttribute("id");
        String username = oAuth2User.getAttribute("username");
        String avatarHash = oAuth2User.getAttribute("avatar");
        String email = oAuth2User.getAttribute("email");

        String avatarUrl = avatarHash != null
                ? "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png"
                : "https://cdn.discordapp.com/embed/avatars/0.png";

        Optional<User> existingUserOpt = userRepository.findByDiscordId(discordId);

        if (existingUserOpt.isPresent() && existingUserOpt.get().isBanned()) {
            throw new OAuth2AuthenticationException("BANNED_USER");
        }

        User user = existingUserOpt
                .map(existingUser -> {
                    existingUser.setDiscordUsername(username);
                    existingUser.setDiscordAvatarUrl(avatarUrl);
                    existingUser.setEmail(email);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .discordId(discordId)
                            .discordUsername(username)
                            .discordAvatarUrl(avatarUrl)
                            .email(email)
                            .build();
                    return userRepository.save(newUser);
                });

        List<GrantedAuthority> authorities = new ArrayList<>(oAuth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        return new DefaultOAuth2User(
                authorities,
                oAuth2User.getAttributes(),
                "id"
        );
    }
}