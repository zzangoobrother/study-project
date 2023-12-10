package com.example.myframework2.mvc.core.mvc;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "dispatcher", urlPatterns = "/", loadOnStartup = 1)
public class DispatcherServlet extends HttpServlet {
    private static final String DEFAULT_REDIRECT_PREFIX = "redirect";

    private RequestMapping requestMapping;

    @Override
    public void init() throws ServletException {
        requestMapping = new RequestMapping();
        requestMapping.init();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Controller controller = requestMapping.getController(request.getRequestURI());

        String uri = controller.execute(request, response);
        if (uri.startsWith(DEFAULT_REDIRECT_PREFIX)) {
            String target = uri.split(":")[1];
            response.sendRedirect(target);
            return;
        }

        RequestDispatcher rd = request.getRequestDispatcher(uri);
        rd.forward(request, response);
    }
}
