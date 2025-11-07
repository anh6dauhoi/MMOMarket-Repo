package com.mmo.config;

import com.mmo.repository.UserRepository;
import com.mmo.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        // Đảm bảo dùng đúng tên cột camelCase như DB
        manager.setUsersByUsernameQuery("SELECT email as username, password, isVerified as enabled FROM Users WHERE email = ? AND isDelete = 0");
        manager.setAuthoritiesByUsernameQuery("SELECT email as username, role as authority FROM Users WHERE email = ? AND isDelete = 0");
        return manager;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = authentication.getName();
                String rawPassword = authentication.getCredentials().toString();
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    throw new DisabledException("Account is not verified. Please check your email for verification.");
                }

                String stored = userDetails.getPassword();
                // Avoid NPE with null/blank stored passwords (e.g., OAuth-only accounts)
                if (stored != null && !stored.isBlank() && passwordEncoder.matches(rawPassword, stored)) {
                    return new UsernamePasswordAuthenticationToken(username, rawPassword, userDetails.getAuthorities());
                }
                // Fallback for legacy plaintext passwords: if equals, authenticate and upgrade hash
                if (stored != null && rawPassword.equals(stored)) {
                    try {
                        User u = userRepository.findByEmailAndIsDelete(username, false);
                        if (u != null) {
                            u.setPassword(passwordEncoder.encode(rawPassword));
                            userRepository.save(u);
                        }
                    } catch (Exception ignored) { }
                    return new UsernamePasswordAuthenticationToken(username, rawPassword, userDetails.getAuthorities());
                }
                throw new BadCredentialsException("Incorrect email or password.");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider, UserDetailsService userDetailsService, CustomOAuth2UserService customOAuth2UserService) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers(HttpMethod.POST, "/api/webhook/sepay").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/webhook/sepay").permitAll()
                        // Public pages for guests (view only)
                        .requestMatchers(HttpMethod.GET,
                                "/", "/homepage",
                                "/category", "/category/**",
                                "/productdetail", "/products", "/products/**",
                                "/shop", "/shop/**", /* shop pages (view only) */
                                "/search", /* search page */
                                "/blog", /* list only */
                                "/blog/**", /* blog detail (view only, like/comment require auth) */
                                "/blog/infinite", /* list API */
                                "/contact",
                                "/api/categories"
                        ).permitAll()
                        // allow guests to submit contact form
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        // Blog POST endpoints - let controller handle authentication and return JSON 401
                        .requestMatchers(HttpMethod.POST,
                                "/blog/*/like",
                                "/blog/*/comment",
                                "/blog/comment/*/like"
                        ).permitAll()
                        // admin area
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        // Chat endpoints - require authentication
                        .requestMatchers("/chat/**").authenticated()
                        // existing allowed posts
                        .requestMatchers(HttpMethod.POST, "/customer/topup").permitAll()
                        // static assets
                        .requestMatchers(
                                "/authen/**", "/welcome", "/error", "/oauth2/**", "/login/**",
                                "/images/**", "/css/**", "/contracts/**", "/js/**",
                                "/customer/css/**", "/authen/css/**", "/admin/css/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/api/webhook/sepay")
                )
                .formLogin((form) -> form
                        .loginPage("/authen/login")
                        .loginProcessingUrl("/authen/login")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin" : "/homepage");
                        })
                        .failureHandler((request, response, exception) -> {
                            String errorMessage = "Incorrect email or password.";
                            if (exception instanceof DisabledException) {
                                errorMessage = "Account is not verified. Please check your email for verification.";
                            } else if (exception instanceof BadCredentialsException) {
                                errorMessage = exception.getMessage();
                            }
                            request.getSession().setAttribute("message", errorMessage);
                            response.sendRedirect("/authen/login?error");
                        })
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .userDetailsService(userDetailsService)
                        .rememberMeParameter("remember-me")
                        .key("MMOMarket-RememberMe-Secret-Key-ChangeMe")
                        .tokenValiditySeconds((int) TimeUnit.DAYS.toSeconds(30))
                )
                .oauth2Login((oauth2) -> oauth2
                        .loginPage("/authen/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin" : "/homepage");
                        })
                )
                .logout((logout) -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/authen/login?logout")
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll()
                )
                .exceptionHandling((exceptions) -> exceptions
                        .accessDeniedPage("/authen/login?error=access_denied")
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendRedirect("/authen/login?error=unauthenticated");
                        })
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()) // Cho phép iframe từ cùng domain
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-src 'self'")
                        )
                );
        return http.build();
    }
}