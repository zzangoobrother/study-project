package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.annotation.Controller;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ControllerScanner {
    private Reflections reflections;

    public ControllerScanner(Object... basePackage) {
        this.reflections = new Reflections(basePackage);
    }

    public Map<Class<?>, Object> getControllers() {
        Set<Class<?>> clazzes = reflections.getTypesAnnotatedWith(Controller.class);
        return instantiateControllers(clazzes);
    }

    private Map<Class<?>, Object> instantiateControllers(Set<Class<?>> clazzes) {
        Map<Class<?>, Object> controllers = new HashMap<>();
        clazzes.forEach(clazz -> {
            controllers.put(clazz, clazz.getName());
        });

        return controllers;
    }
}
