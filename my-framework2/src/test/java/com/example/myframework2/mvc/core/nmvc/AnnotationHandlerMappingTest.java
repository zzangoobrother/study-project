package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.mvc.ModelAndView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationHandlerMappingTest {

    private AnnotationHandlerMapping handlerMapping;
    private MockHttpServletResponse response;

    @BeforeEach
    void setup() {
        handlerMapping = new AnnotationHandlerMapping("com.example.myframework2.mvc.core.nmvc");
        handlerMapping.initialize();

        response = new MockHttpServletResponse();
    }

    @Test
    void list() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        HandlerExecution execution = handlerMapping.getHandler(request);
        ModelAndView mav = execution.handle(request, response);
        mav.getView().render(mav.getModel(), request, response);
        assertEquals("/users/list.jsp", response.getForwardedUrl());
    }

    @Test
    public void show() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/show");
        HandlerExecution execution = handlerMapping.getHandler(request);
        ModelAndView mav = execution.handle(request, response);
        mav.getView().render(mav.getModel(), request, response);
        assertEquals("/users/show.jsp", response.getForwardedUrl());
    }

    @Test
    public void create() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        HandlerExecution execution = handlerMapping.getHandler(request);
        ModelAndView mav = execution.handle(request, response);
        mav.getView().render(mav.getModel(), request, response);
        assertEquals("/users", response.getRedirectedUrl());
    }
}
