package com.mmo.config;

import com.mmo.entity.User;
import com.mmo.repository.UserRepository;
import com.mmo.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom OAuth2UserService to handle Google OAuth2 login.
 * This service ensures that users logging in via Google OAuth2 are properly
 * created in the database and have correct authorities loaded for authorization.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // First, get the user info from Google
        OAuth2User oauth2User = super.loadUser(userRequest);

        // Extract user information from OAuth2User
        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        // Try to find existing user in database
        // Note: User will be created by GlobalModelAttributes if not found
        User user = userRepository.findByEmailAndIsDelete(email, false);

        // Build authorities based on user role from database
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user != null && user.getRole() != null && !user.getRole().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority(user.getRole()));
        } else {
            // Default role for OAuth2 users
            authorities.add(new SimpleGrantedAuthority("CUSTOMER"));
        }

        // Return OAuth2User with proper authorities
        // Use "sub" as the name attribute key (standard for OAuth2)
        return new DefaultOAuth2User(authorities, attributes, "sub");
    }
}

