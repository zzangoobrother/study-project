package com.example.handler.kafka;

import com.example.dto.kafka.outbound.RecordInterface;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Component
@SuppressWarnings({"rawtypes", "unchecked"})
public class RecordDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RecordDispatcher.class);

    private final Map<Class<? extends RecordInterface>, BaseRecordHandler<? extends RecordInterface>> handlerMap = new HashMap<>();
    private final ListableBeanFactory listableBeanFactory;

    public RecordDispatcher(ListableBeanFactory listableBeanFactory) {
        this.listableBeanFactory = listableBeanFactory;
    }

    public <T extends RecordInterface> void dispatchRecord(T record) {
        BaseRecordHandler<T> handler = (BaseRecordHandler<T>)handlerMap.get(record.getClass());

        if (handler != null) {
            handler.handleRecord(record);
            return;
        }

        log.error("Handler not found for request type : {}", record.getClass().getSimpleName());
    }

    @PostConstruct
    private void prepareRecordHandlerMapping() {
        Map<String, BaseRecordHandler> beanHandlers = listableBeanFactory.getBeansOfType(BaseRecordHandler.class);
        for (BaseRecordHandler handler : beanHandlers.values()) {
            Class<? extends RecordInterface> record = extractRecordClass(handler);
            if (record != null) {
                handlerMap.put(record, handler);
            }
        }
    }

    private Class<? extends RecordInterface> extractRecordClass(BaseRecordHandler handler) {
        for (Type type : handler.getClass().getGenericInterfaces()) {
            if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType().equals(BaseRecordHandler.class)) {
                return (Class<? extends RecordInterface>) parameterizedType.getActualTypeArguments()[0];
            }
        }

        return null;
    }
}
