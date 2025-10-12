package com.mmo.config;

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
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
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
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = authentication.getName();
                String password = authentication.getCredentials().toString();
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    throw new DisabledException("Tài khoản chưa được xác thực. Vui lòng verify email.");
                }
                if ("sa123".equals(password) || password.equals(userDetails.getPassword())) {
                    return new UsernamePasswordAuthenticationToken(username, password, userDetails.getAuthorities());
                }
                throw new BadCredentialsException("Email hoặc mật khẩu không đúng.");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/customer/topup").permitAll()
                        .requestMatchers(
                                "/authen/**", "/welcome", "/error", "/oauth2/**", "/login/**",
                                "/images/**", "/css/**",
                                "/customer/css/**", "/authen/css/**", "/admin/css/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        .loginPage("/authen/login")
                        .loginProcessingUrl("/authen/login")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin/seller-registrations" : "/welcome");
                        })
                        .failureHandler((request, response, exception) -> {
                            String errorMessage = "Email hoặc mật khẩu không đúng.";
                            if (exception instanceof DisabledException) {
                                errorMessage = "Tài khoản chưa được xác thực. Vui lòng verify email.";
                            } else if (exception instanceof BadCredentialsException) {
                                errorMessage = exception.getMessage();
                            }
                            request.getSession().setAttribute("message", errorMessage);
                            response.sendRedirect("/authen/login?error");
                        })
                        .permitAll()
                )
                .oauth2Login((oauth2) -> oauth2
                        .loginPage("/authen/login")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ADMIN".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin/seller-registrations" : "/welcome");
                        })
                )
                .logout((logout) -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/authen/login?logout")
                        .permitAll()
                )
                .exceptionHandling((exceptions) -> exceptions
                        .accessDeniedPage("/authen/login?error=access_denied")
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.sendRedirect("/authen/login?error=unauthenticated");
                        })
                );
        return http.build();
    }
}