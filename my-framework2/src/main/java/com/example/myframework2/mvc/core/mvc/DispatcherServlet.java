package com.example.myframework2.mvc.core.mvc;

import com.example.myframework2.mvc.core.nmvc.AnnotationHandlerMapping;
import com.example.myframework2.mvc.core.nmvc.HandlerExecution;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "dispatcher", urlPatterns = "/", loadOnStartup = 1)
public class DispatcherServlet extends HttpServlet {

    private LegacyHandlerMapping lhm;
    private AnnotationHandlerMapping ahm;

    @Override
    public void init() throws ServletException {
        lhm = new LegacyHandlerMapping();
        lhm.init();
        ahm = new AnnotationHandlerMapping("next.controller");
        ahm.initialize();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            Controller controller = lhm.getController(request.getRequestURI());
            if (controller != null) {
                render(request, response, controller.execute(request, response));
            } else {
                HandlerExecution handler = ahm.getHandler(request);
                if (handler == null) {
                    throw new ServletException("");
                }

                render(request, response, handler.handle(request, response));
            }
        } catch (Exception e) {
            throw new ServletException(e.getMessage());
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
        View view = mav.getView();
        view.render(mav.getModel(), request, response);
    }
}
