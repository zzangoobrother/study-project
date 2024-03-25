package com.example.config;

import com.example.domain.customer.CustomerDetailService;
import com.example.enums.ECommerceRole;
import com.example.myframework2.mvc.core.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SimpleSecurityConfig {

    private final CustomerDetailService customerDetailService;

    public SimpleSecurityConfig(CustomerDetailService customerDetailService) {
        this.customerDetailService = customerDetailService;
    }

    AuthenticationManager configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(customerDetailService).passwordEncoder(bPasswordEncoder());
        return auth.build();
    }

    @Bean
    public PasswordEncoder bPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().disable();

        http.formLogin()
                .loginPage("/customer/login")
                .defaultSuccessUrl("/")
                .usernameParameter("email")
                .failureUrl("/customer/login?error=true")
                .and()
                .logout()
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .and()
                .authorizeHttpRequests(authorize -> authorize
                        .antMatchers("/cart/**")
                        .hasRole(String.valueOf(ECommerceRole.CUSTOMER))
                        .antMatchers("/checkout/**").hasRole(String.valueOf(ECommerceRole.CUSTOMER))
                        .antMatchers("/customer/my-page*").hasRole(String.valueOf(ECommerceRole.CUSTOMER))
                        .antMatchers("/**").permitAll());

        http.exceptionHandling().authenticationEntryPoint(new SimpleAuthenticationEntryPoint());

        return http.build();
    }
}
