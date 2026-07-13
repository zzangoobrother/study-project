package com.example.hello;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

// 워크플로 "계약". 시작 측과 실행 측이 이 인터페이스를 공유한다.
@WorkflowInterface
public interface HelloWorkflow {

    @WorkflowMethod
    String sayHello(String name);
}
