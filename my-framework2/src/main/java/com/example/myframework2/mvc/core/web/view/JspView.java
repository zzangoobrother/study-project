package com.example.myframework2.mvc.core.web.view;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;

public class JspView implements View {
    private static final String DEFAULT_REDIRECT_PREFIX = "redirect";

    private final String uri;

    public JspView(String uri) {
        this.uri = uri;
    }


    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (uri.startsWith(DEFAULT_REDIRECT_PREFIX)) {
            String target = uri.split(":")[1];
            response.sendRedirect(target);
            return;
        }

        Set<String> keys = model.keySet();
        for (String key : keys) {
            request.setAttribute(key, model.get(key));
        }

        RequestDispatcher rd = request.getRequestDispatcher(uri);
        rd.forward(request, response);
    }
}
