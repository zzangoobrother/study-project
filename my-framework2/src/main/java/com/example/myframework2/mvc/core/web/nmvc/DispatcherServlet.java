package com.example.myframework2.mvc.core.web.nmvc;

import com.example.myframework2.mvc.core.mvc.*;
import com.example.myframework2.mvc.core.web.mvc.ControllerHandlerAdapter;
import com.example.myframework2.mvc.core.web.mvc.HandlerMapping;
import com.example.myframework2.mvc.core.web.mvc.LegacyHandlerMapping;
import com.example.myframework2.mvc.core.web.view.ModelAndView;
import com.example.myframework2.mvc.core.web.view.View;
import com.google.common.collect.Lists;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet(name = "dispatcher", urlPatterns = "/", loadOnStartup = 1)
public class DispatcherServlet extends HttpServlet {

    private List<HandlerMapping> mappings = Lists.newArrayList();
    private List<HandlerAdapter> handlerAdapters = Lists.newArrayList();

    @Override
    public void init() throws ServletException {
        LegacyHandlerMapping lhm = new LegacyHandlerMapping();
        lhm.init();
        AnnotationHandlerMapping ahm = new AnnotationHandlerMapping("com.example.myframework2.mvc");
        ahm.initialize();

        mappings.add(lhm);
        mappings.add(ahm);

        handlerAdapters.add(new ControllerHandlerAdapter());
        handlerAdapters.add(new HandlerExecutionHandlerAdapter());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Object handler = getHandler(request);
        if (handler == null) {
            throw new IllegalArgumentException();
        }

        try {
            ModelAndView modelAndView = execute(handler, request, response);
            View view = modelAndView.getView();
            view.render(modelAndView.getModel(), request, response);
        } catch (Exception e) {
            throw new ServletException(e.getMessage());
        }
    }

    private Object getHandler(HttpServletRequest request) {
        for (HandlerMapping handlerMapping : mappings) {
            Object handler = handlerMapping.getHandler(request);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    private ModelAndView execute(Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception {
        for (HandlerAdapter handlerAdapter : handlerAdapters) {
            if (handlerAdapter.supports(handler)) {
                return handlerAdapter.handle(request, response, handler);
            }
        }

        return null;
    }

    private void render(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
        View view = mav.getView();
        view.render(mav.getModel(), request, response);
    }
}
