package com.example.hellospring;

import com.example.hellospring.api.ApiTemplate;
import com.example.hellospring.api.ErApiExRateExtractor;
import com.example.hellospring.api.SimpleApiExecutor;
import com.example.hellospring.exrate.CachedExRateProvider;
import com.example.hellospring.exrate.WebApiExRateProvider;
import com.example.hellospring.payment.ExRateProvider;
import com.example.hellospring.payment.PaymentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService(cachedExRateProvider(), clock());
    }

    @Bean
    public ExRateProvider cachedExRateProvider() {
        return new CachedExRateProvider(exRateProvider());
    }

    @Bean
    public ApiTemplate apiTemplate() {
        return new ApiTemplate(new SimpleApiExecutor(), new ErApiExRateExtractor());
    }

    @Bean
    public ExRateProvider exRateProvider() {
        return new WebApiExRateProvider(apiTemplate());
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
