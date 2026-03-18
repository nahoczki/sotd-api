package sotd.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers user-scoped upstream auth checks on `/api/users/{appUserId}/...` routes.
 */
@Configuration
public class UpstreamAuthWebConfig implements WebMvcConfigurer {

    private final UpstreamAuthInterceptor upstreamAuthInterceptor;

    public UpstreamAuthWebConfig(UpstreamAuthInterceptor upstreamAuthInterceptor) {
        this.upstreamAuthInterceptor = upstreamAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(upstreamAuthInterceptor)
                .addPathPatterns("/api/users/*/**");
    }
}
