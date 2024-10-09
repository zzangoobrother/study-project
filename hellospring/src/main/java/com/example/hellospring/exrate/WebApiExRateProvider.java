package com.example.hellospring.exrate;

import com.example.hellospring.api.ApiTemplate;
import com.example.hellospring.api.ErApiExRateExtractor;
import com.example.hellospring.api.HttpClientApiExecutor;
import com.example.hellospring.payment.ExRateProvider;

import java.math.BigDecimal;

public class WebApiExRateProvider implements ExRateProvider {

    ApiTemplate apiTemplate = new ApiTemplate();

    @Override
    public BigDecimal getExRate(String currency) {
        String url = "https://open.er-api.com/v6/latest/" + currency;

        return apiTemplate.getExRate(url, new HttpClientApiExecutor(), new ErApiExRateExtractor());
    }
}
