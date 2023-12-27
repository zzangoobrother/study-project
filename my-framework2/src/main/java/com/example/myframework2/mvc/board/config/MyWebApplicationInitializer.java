package com.example.myframework2.mvc.board.config;

import com.example.myframework2.mvc.core.di.factory.AnnotationConfigApplicationContext;
import com.example.myframework2.mvc.core.di.factory.ApplicationContext;
import com.example.myframework2.mvc.core.web.WebApplicationInitializer;
import com.example.myframework2.mvc.core.web.nmvc.AnnotationHandlerMapping;
import com.example.myframework2.mvc.core.web.nmvc.DispatcherServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

public class MyWebApplicationInitializer implements WebApplicationInitializer {
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        ApplicationContext ac = new AnnotationConfigApplicationContext(MyConfiguration.class);
        AnnotationHandlerMapping ahm = new AnnotationHandlerMapping(ac);
        ahm.initialize();

        ServletRegistration.Dynamic dispatcher = servletContext.addServlet("dispatcher", new DispatcherServlet(ahm));
        dispatcher.setLoadOnStartup(1);
        dispatcher.addMapping("/");
    }
}
