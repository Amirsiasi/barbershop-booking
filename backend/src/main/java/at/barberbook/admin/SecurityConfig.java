package at.barberbook.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import at.barberbook.common.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Public booking API is open, everything under /api/admin needs a login (HTTP Basic over HTTPS).
 * The API is stateless (no session cookie), so CSRF protection is not needed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint jsonEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(jsonEntryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonEntryPoint));
        return http.build();
    }

    /** 401 as JSON and WITHOUT the WWW-Authenticate header, so the browser shows our login form, not its own popup. */
    @Bean
    AuthenticationEntryPoint jsonEntryPoint(ObjectMapper json) {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getWriter(),
                    new ApiError(401, "Unauthorized", "Benutzername oder Passwort falsch.", request.getRequestURI(), null));
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(AdminProperties props, PasswordEncoder encoder) {
        if (props.password() == null || props.password().isBlank()) {
            throw new IllegalStateException("Set app.admin.password (env ADMIN_PASSWORD) before starting the app.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(props.username())
                .password(encoder.encode(props.password()))
                .roles("ADMIN")
                .build());
    }

    /**
     * Only needed if the frontend runs on a different domain than the API.
     * With the included nginx / Vite proxy both are on the same origin and this list stays empty.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins.stream().filter(o -> !o.isBlank()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
