package com.example.hello;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 워크플로를 시작하는 진입점. WorkflowClient는 Temporal 스타터가 자동 구성한다.
@RestController
public class HelloController {

    private final WorkflowClient workflowClient;

    public HelloController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/hello")
    public String hello(@RequestParam(defaultValue = "Temporal") String name) {
        HelloWorkflow workflow = workflowClient.newWorkflowStub(
                HelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        // 워커가 폴링하는 큐와 반드시 같아야 한다(라우팅 키).
                        .setTaskQueue("HELLO_TASK_QUEUE")
                        .build());
        // 동기 실행: 결과가 나올 때까지 블로킹(학습용).
        return workflow.sayHello(name);
    }
}
