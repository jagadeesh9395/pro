package com.tal.pro.security;

import com.tal.pro.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {
    @Autowired
    private Environment env;

    private final UserDetailsServiceImpl userDetailsService;

    public WebSecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        authProvider.setHideUserNotFoundExceptions(false); // Show user not found exceptions
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configure H2 console access
        http
                // Enable CORS first
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure CSRF
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository())
                            .ignoringRequestMatchers(
                                    "/h2-console/**",
                                    "/api/auth/**"
                            );
                    // Enable CSRF for all requests
                    csrf.requireCsrfProtectionMatcher(
                            new AndRequestMatcher(
                                    CsrfFilter.DEFAULT_CSRF_MATCHER,
                                    new NegatedRequestMatcher(new AntPathRequestMatcher("/api/auth/**"))
                            )
                    );
                })
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable())
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                        .invalidSessionUrl("/auth/login?expired")
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/auth/login?expired")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/home",
                                "/auth/**",
                                "/api/auth/**",
                                "/api/test/**",
                                "/h2-console/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/recruiter/**").hasRole("RECRUITER")
                        .requestMatchers("/candidate/**").hasRole("CANDIDATE")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Allow access to job application endpoints for any authenticated user
                        .requestMatchers("/jobs/*/apply").authenticated()
                        .requestMatchers(
                                "/api/education/**",
                                "/api/experience/**"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureHandler((request, response, exception) -> {
                            String errorMessage = "Invalid username or password";
                            if (exception.getMessage().equals("Bad credentials")) {
                                errorMessage = "Invalid username or password";
                            } else if (exception.getMessage().contains("User is disabled")) {
                                errorMessage = "Your account is disabled. Please contact support.";
                            } else if (exception.getMessage().contains("User account is locked")) {
                                errorMessage = "Your account is locked. Please try again later or contact support.";
                            } else if (exception.getMessage().contains("User account has expired")) {
                                errorMessage = "Your account has expired. Please contact support.";
                            }
                            response.sendRedirect("/auth/login?error=true&message=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8));
                        })
                        .successHandler((request, response, authentication) -> {
                            // Get the authenticated user's authorities
                            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                            String targetUrl = "/dashboard"; // Default fallback

                            // Simple role-based redirection without messing with the authentication
                            for (GrantedAuthority authority : authorities) {
                                if (authority.getAuthority().equals("ROLE_RECRUITER")) {
                                    targetUrl = "/recruiter/dashboard";
                                    break;
                                } else if (authority.getAuthority().equals("ROLE_CANDIDATE")) {
                                    targetUrl = "/candidate/dashboard";
                                    break;
                                } else if (authority.getAuthority().equals("ROLE_ADMIN")) {
                                    targetUrl = "/admin/dashboard";
                                    break;
                                }
                            }
                            
                            // Just redirect to the target URL without touching the authentication
                            response.sendRedirect(targetUrl);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:8080",
            "http://127.0.0.1:8080"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "authorization",
            "content-type",
            "x-csrf-token",
            "x-requested-with"
        ));
        configuration.setExposedHeaders(Arrays.asList(
            "authorization",
            "content-type",
            "x-csrf-token",
            "x-requested-with"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
