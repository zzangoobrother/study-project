package com.example.inflearncorespringsecurityproject.security.configs;

import com.example.inflearncorespringsecurityproject.security.common.AjaxLoginAuthenticationEntryPoint;
import com.example.inflearncorespringsecurityproject.security.filter.AjaxLoginProcessingFilter;
import com.example.inflearncorespringsecurityproject.security.handler.AjaxAccessDeniedHandler;
import com.example.inflearncorespringsecurityproject.security.handler.AjaxAuthenticationFailureHandler;
import com.example.inflearncorespringsecurityproject.security.handler.AjaxAuthenticationSuccessHandler;
import com.example.inflearncorespringsecurityproject.security.provider.AjaxAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Order(0)
@Configuration
public class AjaxSecurityConfig extends WebSecurityConfigurerAdapter {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AjaxSecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(ajaxAuthenticationProvider());
    }

    @Bean
    protected AuthenticationProvider ajaxAuthenticationProvider() {
        return new AjaxAuthenticationProvider(userDetailsService, passwordEncoder);
    }

    @Bean
    public AjaxLoginProcessingFilter ajaxLoginProcessingFilter() throws Exception {
        AjaxLoginProcessingFilter ajaxLoginProcessingFilter = new AjaxLoginProcessingFilter();
        ajaxLoginProcessingFilter.setAuthenticationManager(authenticationManagerBean());
        ajaxLoginProcessingFilter.setAuthenticationSuccessHandler(ajaxAuthenticationSuccessHandler());
        ajaxLoginProcessingFilter.setAuthenticationFailureHandler(ajaxAuthenticationFailureHandler());
        return ajaxLoginProcessingFilter;
    }

    @Bean
    public AuthenticationSuccessHandler ajaxAuthenticationSuccessHandler() {
        return new AjaxAuthenticationSuccessHandler();
    }

    @Bean
    public AuthenticationFailureHandler ajaxAuthenticationFailureHandler() {
        return new AjaxAuthenticationFailureHandler();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.antMatcher("/api/**")
                .authorizeRequests()
                .antMatchers("/api/messages").hasRole("MANAGER")
                .anyRequest().authenticated();

        http.addFilterBefore(ajaxLoginProcessingFilter(), UsernamePasswordAuthenticationFilter.class);

        http.exceptionHandling()
                .authenticationEntryPoint(new AjaxLoginAuthenticationEntryPoint())
                .accessDeniedHandler(ajaxAccessDeniedHandler());

        http.csrf().disable();
    }

    @Bean
    public AccessDeniedHandler ajaxAccessDeniedHandler() {
        return new AjaxAccessDeniedHandler();
    }
}
