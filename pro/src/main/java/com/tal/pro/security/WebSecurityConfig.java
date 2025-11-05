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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

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
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/h2-console/**",
                    "/api/auth/**"
                )
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.disable())
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
                    
                    // Determine the target URL based on the user's role
                    String targetUrl = "/dashboard"; // Default fallback
                    
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
                    
                    // Handle session fixation protection
                    if (!response.isCommitted()) {
                        // Clear any existing authentication
                        SecurityContextHolder.getContext().setAuthentication(null);
                        
                        // Create a new authentication token with the same credentials
                        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                            authentication.getPrincipal(),
                            authentication.getCredentials(),
                            authentication.getAuthorities()
                        );
                        newAuth.setDetails(authentication.getDetails());
                        
                        // Set the new authentication in the security context
                        SecurityContextHolder.getContext().setAuthentication(newAuth);
                        
                        // Redirect to the target URL
                        response.sendRedirect(targetUrl);
                    }
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
        configuration.setAllowedOrigins(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private boolean isH2ConsoleEnabled() {
        return Boolean.parseBoolean(env.getProperty("spring.h2.console.enabled", "false"));
    }
}
