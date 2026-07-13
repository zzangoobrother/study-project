package com.example.order;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final WorkflowClient workflowClient;

    public OrderController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    public OrderResult createOrder(@RequestBody OrderRequest request) {
        OrderWorkflow workflow = workflowClient.newWorkflowStub(
                OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("ORDER_TASK_QUEUE")
                        // WorkflowId를 주문 ID로 지정 → 같은 주문 중복 실행 방지(멱등성)
                        .setWorkflowId("order-" + request.orderId())
                        .build());
        return workflow.processOrder(request);
    }
}
