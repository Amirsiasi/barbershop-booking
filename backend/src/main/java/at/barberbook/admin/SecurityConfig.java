package at.barberbook.admin;

import at.barberbook.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Who may call what. The public booking API is open; everything under /api/admin needs a login (HTTP Basic
 * over HTTPS, checked against {@link at.barberbook.staff.StaffUserDetailsService}).
 *
 * <ul>
 *   <li>OWNER: everything</li>
 *   <li>BARBER: calendar, requests and blocked times - the "only my own appointments" rule is checked in the
 *       controllers, because it depends on the appointment</li>
 *   <li>Team, logins, prices, shop photo/rating and the chat simulator: OWNER only</li>
 * </ul>
 * The API is stateless (no session cookie), so CSRF protection is not needed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                write(json, response, 401, "Unauthorized", "Benutzername oder Passwort falsch.", request.getRequestURI());
        AccessDeniedHandler forbidden = (request, response, ex) ->
                write(json, response, 403, "Forbidden", "Dafür hast du keine Berechtigung.", request.getRequestURI());

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/barbers/**", "/api/admin/staff/**",
                                "/api/admin/services/**", "/api/admin/chat/**", "/api/admin/shop/**").hasRole("OWNER")
                        .requestMatchers("/api/admin/**").hasAnyRole("OWNER", "BARBER")
                        .anyRequest().permitAll())
                // 401 as JSON and WITHOUT the WWW-Authenticate header, so the browser shows our login form, not its popup
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
        return http.build();
    }

    private static void write(ObjectMapper json, HttpServletResponse response, int status, String error,
                              String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), new ApiError(status, error, message, path, null));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Only needed if the frontend runs on a different domain than the API.
     * With the included nginx / Vite proxy both are on the same origin and this list stays empty.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins.stream().filter(o -> !o.isBlank()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
