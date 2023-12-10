package com.example.myframework2.mvc.board.web;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "dispatcher", urlPatterns = "/", loadOnStartup = 1)
public class DispatcherServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Controller controller = RequestMapping.getController(request.getRequestURI());

        String uri = controller.execute(request, response);
        if (uri.startsWith("redirect")) {
            String target = uri.split(":")[1];
            response.sendRedirect(target);
            return;
        }

        RequestDispatcher rd = request.getRequestDispatcher(uri);
        rd.forward(request, response);
    }
}
