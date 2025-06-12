package com.example.handler.websocket;

import com.example.dto.websocket.inbound.BaseRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RequestHandlerDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RequestHandlerDispatcher.class);

    private final Map<Class<? extends BaseRequest>, BaseRequestHandler<? extends BaseRequest>> handlerMap = new HashMap<>();
    private final ListableBeanFactory listableBeanFactory;

    public RequestHandlerDispatcher(ListableBeanFactory listableBeanFactory) {
        this.listableBeanFactory = listableBeanFactory;
    }

    @PostConstruct
    private void prepareRequestHandlerMapping() {
        Map<String, BaseRequestHandler> beanHandlers = listableBeanFactory.getBeansOfType(BaseRequestHandler.class);
        for (BaseRequestHandler handler : beanHandlers.values()) {

        }
    }

    private Class<? extends BaseRequest> extractRequestClass(BaseRequestHandler handler) {
        for (Type type : handler.getClass().getGenericInterfaces()) {
            if (type instanceof ParameterizedType && parameterizedType.getRawType().equals(BaseRequestHandler.class)) {
                return (Class<? extends BaseRequest>) parameterizedType.getActualTypeArguments();
            }
        }
    }
}
