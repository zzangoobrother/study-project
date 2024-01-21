package com.example.pricecompareredis.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(title = "GoodChoice Backend API", version = "1.0"),
        security = {@SecurityRequirement(name = "x-api-key")}
)
@SecuritySchemes({
        @SecurityScheme(name = "x-api-key", type = SecuritySchemeType.APIKEY, description = "API Key", in = SecuritySchemeIn.HEADER, paramName = "x-api-key")
})
@Configuration
public class SwaggerConfig {
}
