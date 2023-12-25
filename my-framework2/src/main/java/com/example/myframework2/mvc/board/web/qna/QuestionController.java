package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.board.model.Question;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.service.QnaService;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.example.myframework2.mvc.core.web.nmvc.AbstractNewController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class QuestionController extends AbstractNewController {
    private QnaService qnaService;
    private QuestionDao questionDao;
    private AnswerDao answerDao;

    @Inject
    public QuestionController(QnaService qnaService, QuestionDao questionDao, AnswerDao answerDao) {
        this.qnaService = qnaService;
        this.questionDao = questionDao;
        this.answerDao = answerDao;
    }

    @RequestMapping("/qna/show")
    public ModelAndView show(HttpServletRequest request, HttpServletResponse response) {
        Long questionId = Long.parseLong(request.getParameter("questionId"));

        return jspView("/qna/show.jsp")
                .addObject("question", questionDao.findById(questionId))
                .addObject("answers", answerDao.findAllByQuestionId(questionId));
    }

    @RequestMapping("/qna/form")
    public ModelAndView createForm(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return jspView("/qna/form.jsp");
    }

    @RequestMapping(value = "/qna/create", method = RequestMethod.POST)
    public ModelAndView create(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        User user = UserSessionUtils.getUserSession(request.getSession());
        Question question = new Question(user.getUserId(), request.getParameter("title"), request.getParameter("contents"));

        questionDao.insert(question);

        return jspView("redirect:/");
    }

    @RequestMapping("/qna/updateForm")
    public ModelAndView updateForm(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        long questionId = Long.parseLong(request.getParameter("questionId"));
        Question question = questionDao.findById(questionId);

        if (!question.isSameUser(UserSessionUtils.getUserSession(request.getSession()))) {
            throw new IllegalStateException();
        }

        return jspView("/qna/update.jsp")
                .addObject("question", question);
    }

    @RequestMapping(value = "/qna/update", method = RequestMethod.POST)
    public ModelAndView update(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        long questionId = Long.parseLong(request.getParameter("questionId"));
        Question question = questionDao.findById(questionId);
        if (!question.isSameUser(UserSessionUtils.getUserSession(request.getSession()))) {
            throw new IllegalStateException();
        }

        Question newQuestion = new Question(question.getWriter(), request.getParameter("title"), request.getParameter("contents"));
        question.update(newQuestion);
        questionDao.update(question);

        return jspView("redirect:/");
    }

    @RequestMapping(value = "/qna/delete", method = RequestMethod.POST)
    public ModelAndView delete(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        long questionId = Long.parseLong(request.getParameter("questionId"));
        try {
            qnaService.deleteQuestion(questionId, UserSessionUtils.getUserSession(request.getSession()));
            return jspView("redirect:/");
        } catch (Exception e) {
            return jspView("show.jsp")
                    .addObject("question", qnaService.findById(questionId))
                    .addObject("answers", qnaService.findAllByQuestionId(questionId))
                    .addObject("errorMessage", e.getMessage());
        }
    }
}
