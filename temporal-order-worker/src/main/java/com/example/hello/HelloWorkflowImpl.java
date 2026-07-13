package com.example.hello;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

// 워크플로 구현. 결정론적이어야 하므로 실제 일은 Activity 스텁을 통해 위임한다.
public class HelloWorkflowImpl implements HelloWorkflow {

    // Activity 호출 옵션. 재시도 정책은 다음 단계에서 추가한다.
    private final HelloActivity activity = Workflow.newActivityStub(
            HelloActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .build());

    @Override
    public String sayHello(String name) {
        return activity.composeGreeting(name);
    }
}
