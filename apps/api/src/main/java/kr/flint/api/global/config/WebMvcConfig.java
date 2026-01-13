package kr.flint.api.global.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import kr.flint.api.global.security.annotation.CurrentUserResolver;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api/v1";

    private final CurrentUserResolver currentUserResolver;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                c -> c.isAnnotationPresent(RestController.class)
                        && c.getPackageName().startsWith("kr.flint.api.")
                        && !c.getPackageName().contains(".config"));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
