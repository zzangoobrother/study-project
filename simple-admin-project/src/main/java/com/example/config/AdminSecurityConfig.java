package com.example.config;

import com.example.service.AdminUserDetailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    public static final String DEFAULT_HOME_URL = "/";

    private final AdminUserDetailService adminUserDetailService;

    public AdminSecurityConfig(AdminUserDetailService adminUserDetailService) {
        this.adminUserDetailService = adminUserDetailService;
    }

    @Bean
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(adminUserDetailService).passwordEncoder(bPasswordEncoder());
    }

    @Bean
    public PasswordEncoder bPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().disable();

        http.authorizeHttpRequests()
                .antMatchers("/img/**", "/js/**", "/css/**", "/scss/**", "/vendor/**",
                                "/users/register", "/error"
                )
                .permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin()
                .defaultSuccessUrl(DEFAULT_HOME_URL)
                .permitAll()
                .and()
                .logout()
                .logoutSuccessUrl(DEFAULT_HOME_URL);

        return http.build();
    }
}
