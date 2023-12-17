package com.example.myframework2.mvc.core.mvc;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JspView implements View {
    private static final String DEFAULT_REDIRECT_PREFIX = "redirect";

    private final String uri;

    public JspView(String uri) {
        this.uri = uri;
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (uri.startsWith(DEFAULT_REDIRECT_PREFIX)) {
            String target = uri.split(":")[1];
            response.sendRedirect(target);
            return;
        }

        RequestDispatcher rd = request.getRequestDispatcher(uri);
        rd.forward(request, response);
    }
}
