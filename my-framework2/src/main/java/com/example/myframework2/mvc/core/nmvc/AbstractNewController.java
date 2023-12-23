package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.mvc.JsonView;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.ModelAndView;

public abstract class AbstractNewController {
    protected ModelAndView jspView(String forwardUrl) {
        return new ModelAndView(new JspView(forwardUrl));
    }

    protected ModelAndView jsonView() {
        return new ModelAndView(new JsonView());
    }
}
