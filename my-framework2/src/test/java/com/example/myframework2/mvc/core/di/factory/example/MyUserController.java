package com.example.myframework2.mvc.core.di.factory.example;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Inject;

@Controller
public class MyUserController {
    private MyUserService myUserService;

    @Inject
    public void setMyUserService(MyUserService myUserService) {
        this.myUserService = myUserService;
    }

    public MyUserService getMyUserService() {
        return myUserService;
    }
}
