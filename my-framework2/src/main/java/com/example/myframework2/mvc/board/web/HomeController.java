package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.example.myframework2.mvc.core.web.nmvc.AbstractNewController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class HomeController extends AbstractNewController {
    private QuestionDao questionDao;

    @Inject
    public HomeController(QuestionDao questionDao) {
        this.questionDao = questionDao;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        return jspView("home.jsp")
                .addObject("questions", questionDao.findAll());
    }
}
