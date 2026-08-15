package com.loopers.config.web

import com.loopers.support.auth.AdminAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 이 프로젝트의 첫 WebMvcConfigurer.
 *
 * WebMvcConfigurer 를 구현하는 것은 @EnableWebMvc 와 다르다.
 * 전자는 스프링 부트의 MVC 자동 설정에 얹는 것이고, 후자는 자동 설정을 통째로 끈다.
 * 여기서 @EnableWebMvc 를 붙이면 Jackson 커스터마이징과 에러 처리가 함께 사라진다.
 */
@Configuration
class WebConfig(
    private val adminAuthInterceptor: AdminAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(ADMIN_PATH_PATTERN)
    }

    companion object {
        private const val ADMIN_PATH_PATTERN = "/api-admin/**"
    }
}
