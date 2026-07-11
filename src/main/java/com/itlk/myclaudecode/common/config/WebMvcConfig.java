package com.itlk.myclaudecode.common.config;

import com.itlk.myclaudecode.auth.interceptor.AuthInterceptor;
import com.itlk.myclaudecode.notification.interceptor.AdminAuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/admin/**",
                        "/api/yjb/qr-code",
                        "/api/yjb/qr-state/**",
                        "/",
                        "/index.html",
                        "/admin.html",
                        "/favicon.ico",
                        "/logo1.png",
                        "/error",
                        "/assets/**"
                );

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin.html")
                .addResourceLocations("classpath:/admin/");
        registry.addResourceHandler("/logo1.png")
                .addResourceLocations("classpath:/admin/");
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Vue Router History 模式 fallback
        // 设置最低优先级，确保 @RestController 和 @Controller 优先匹配
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}/{path2:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
