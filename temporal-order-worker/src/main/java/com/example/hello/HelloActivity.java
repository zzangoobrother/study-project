package com.example.hello;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

// Activity "계약". 부수효과(외부 호출 등)를 담당한다.
@ActivityInterface
public interface HelloActivity {

    @ActivityMethod
    String composeGreeting(String name);
}
