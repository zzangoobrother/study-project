package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.example.myframework2.mvc.core.web.nmvc.AbstractNewController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Controller
public class UserController extends AbstractNewController {

    private UserDao userDao;

    @Inject
    public UserController(UserDao userDao) {
        this.userDao = userDao;
    }

    @RequestMapping("/users")
    public ModelAndView list(HttpServletRequest request, HttpServletResponse response) {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        ModelAndView mav = jspView("/user/list.jsp");
        mav.addObject("users", userDao.findAll());
        return mav;
    }

    @RequestMapping("/users/profile")
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User user = userDao.findByUserId(userId);
        if (user == null) {
            throw new NullPointerException("사용자를 찾을 수 없습니다.");
        }

        return jspView("/user/profile.jsp")
                .addObject("user", user);
    }

    @RequestMapping("/users/form")
    public ModelAndView form(HttpServletRequest request, HttpServletResponse response) {
        return jspView("/user/form.jsp");
    }

    @RequestMapping(value = "/users/create", method = RequestMethod.POST)
    public ModelAndView create(HttpServletRequest request, HttpServletResponse response) {
        User user = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));

        userDao.insert(user);
        return jspView("redirect:/");
    }

    @RequestMapping("/users/updateForm")
    public ModelAndView updateForm(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User user = userDao.findByUserId(userId);

        if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
            throw new IllegalStateException();
        }

        return jspView("/user/updateForm.jsp")
                .addObject("user", user);
    }

    @RequestMapping(value =  "/users/update", method = RequestMethod.POST)
    public ModelAndView update(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User user = userDao.findByUserId(userId);
        if (!UserSessionUtils.isSameUser(request.getSession(), user)) {
            throw new IllegalStateException();
        }

        if (user == null) {
            throw new IllegalStateException();
        }

        User updateUser = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
        user.update(updateUser);
        userDao.update(user);

        return jspView("redirect:/");
    }

    @RequestMapping("/users/loginForm")
    public ModelAndView loginForm(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return jspView("/user/login.jsp");
    }

    @RequestMapping(value = "/users/login", method = RequestMethod.POST)
    public ModelAndView login(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User findUser = userDao.findByUserId(userId);

        if (findUser == null) {
            return jspView("/user/login.jsp");
        }

        if (!findUser.matchPassword(request.getParameter("password"))) {
            return jspView("/user/login.jsp");
        }

        HttpSession session = request.getSession();
        session.setAttribute("user", findUser);

        return jspView("redirect:/");
    }

    @RequestMapping("/users/logout")
    public ModelAndView logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        session.removeAttribute(UserSessionUtils.USER_SESSION_KEY);
        return jspView("redirect:/");
    }
}
