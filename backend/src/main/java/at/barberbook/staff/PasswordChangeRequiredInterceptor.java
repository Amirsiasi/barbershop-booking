package at.barberbook.staff;

import at.barberbook.common.Exceptions.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * After the owner set a start password, the barber can do nothing but choose an own password.
 * Enforced on the server, so the rule holds even if someone calls the API directly.
 */
@Configuration
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/me", "/api/admin/me/password");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StaffPrincipal p && p.mustChangePassword()) {
            throw new ForbiddenException("Bitte lege zuerst ein eigenes Passwort fest.");
        }
        return true;
    }
}
