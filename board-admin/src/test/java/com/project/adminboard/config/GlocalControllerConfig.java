package com.project.adminboard.config;

import com.project.adminboard.service.VisitCountService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.event.annotation.BeforeTestMethod;

import static org.mockito.BDDMockito.given;

@TestConfiguration
public class GlocalControllerConfig {

    @MockBean
    private
    VisitCountService visitCountService;

    @BeforeTestMethod
    public void securitySetup() {
        given(visitCountService.visitCount())
                .willReturn(0L);
    }
}
