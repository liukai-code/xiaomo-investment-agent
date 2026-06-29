package com.itlk.myclaudecode.common.config;

import com.itlk.myclaudecode.auth.interceptor.AuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/admin/**",
                        "/",
                        "/index.html",
                        "/admin.html",
                        "/favicon.ico",
                        "/error",
                        "/assets/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Vue Router History 模式 fallback
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}/{path2:[^\\.]*}").setViewName("forward:/index.html");
    }
}
