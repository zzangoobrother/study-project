package com.example.myframework2.mvc.core.di.factory.example;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.mvc.ModelAndView;
import com.example.myframework2.mvc.core.nmvc.AbstractNewController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class QnaController extends AbstractNewController {

    private MyQnaService qnaService;

    @Inject
    public QnaController(MyQnaService qnaService) {
        this.qnaService = qnaService;
    }

    public MyQnaService getQnaService() {
        return qnaService;
    }

    @RequestMapping("/questions")
    public ModelAndView list(HttpServletRequest request, HttpServletResponse response) {
        return jspView("/qna/list.jsp");
    }
}
