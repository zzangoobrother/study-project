package com.example.config;

import com.example.service.AdminUserDetailService;
import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableWebSecurity
//@EnableGlobalMethodSecurity(
//        securedEnabled = true,
//        jsr250Enabled = true,
//        prePostEnabled = true
//)
public class AdminSecurityConfig {

    public static final String DEFAULT_HOME_URL = "/";

    private final AdminUserDetailService adminUserDetailService;

    public AdminSecurityConfig(AdminUserDetailService adminUserDetailService) {
        this.adminUserDetailService = adminUserDetailService;
    }

//    public AuthenticationManager configure(AuthenticationManagerBuilder auth) throws Exception {
//        auth.userDetailsService(adminUserDetailService).passwordEncoder(bPasswordEncoder());
//        return auth.build();
//    }
//
//    @Bean
//    public PasswordEncoder bPasswordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http.csrf().disable()
//                .cors().disable()
//                .httpBasic().disable();
//
//        http.authorizeHttpRequests()
//                .antMatchers("/img/**", "/js/**", "/css/**", "/scss/**", "/vendor/**",
//                                "/users/register", "/error"
//                )
//                .permitAll()
//                .anyRequest().authenticated()
//                .and()
//                .formLogin()
//                .loginPage("/")
//                .defaultSuccessUrl(DEFAULT_HOME_URL)
//                .permitAll()
//                .and()
//                .logout()
//                .logoutSuccessUrl(DEFAULT_HOME_URL);
//
//        return http.build();
//    }
}
