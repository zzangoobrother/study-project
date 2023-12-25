package com.example.myframework2.mvc.core.web.nmvc;

import com.example.myframework2.mvc.core.web.view.JsonView;
import com.example.myframework2.mvc.core.web.view.JspView;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

public abstract class AbstractNewController {
    protected ModelAndView jspView(String forwardUrl) {
        return new ModelAndView(new JspView(forwardUrl));
    }

    protected ModelAndView jsonView() {
        return new ModelAndView(new JsonView());
    }
}
