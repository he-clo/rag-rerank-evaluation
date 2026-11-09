package com.gb.rag.Configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import  com.gb.rag.interceptor.preInterceptor;
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private preInterceptor preInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(preInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/test01/register",
                        "/test01/login",
                        "/test01/rerank"
                );
    }
}
