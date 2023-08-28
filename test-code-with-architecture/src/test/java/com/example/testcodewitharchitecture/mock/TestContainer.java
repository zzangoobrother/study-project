package com.example.testcodewitharchitecture.mock;

import com.example.testcodewitharchitecture.common.service.port.ClockHolder;
import com.example.testcodewitharchitecture.common.service.port.UuidHolder;
import com.example.testcodewitharchitecture.post.controller.PostController;
import com.example.testcodewitharchitecture.post.controller.PostCreateController;
import com.example.testcodewitharchitecture.post.controller.port.PostService;
import com.example.testcodewitharchitecture.post.service.PostServiceImpl;
import com.example.testcodewitharchitecture.post.service.port.PostRepository;
import com.example.testcodewitharchitecture.user.controller.UserController;
import com.example.testcodewitharchitecture.user.controller.UserCreateController;
import com.example.testcodewitharchitecture.user.controller.port.*;
import com.example.testcodewitharchitecture.user.service.CertificationService;
import com.example.testcodewitharchitecture.user.service.UserServiceImpl;
import com.example.testcodewitharchitecture.user.service.port.MailSender;
import com.example.testcodewitharchitecture.user.service.port.UserRepository;

public class TestContainer {

    public final MailSender mailSender;
    public final UserRepository userRepository;
    public final PostRepository postRepository;
    public final UserReadService userReadService;
    public final UserCreateService userCreateService;
    public final UserUpdateService userUpdateService;
    public final AuthenticationService authenticationService;
    public final PostService postService;
    public final CertificationService certificationService;
    public final UserController userController;
    public final UserCreateController userCreateController;
    public final PostCreateController postCreateController;
    public final PostController postController;

    public TestContainer(ClockHolder clockHolder, UuidHolder uuidHolder) {
        this.mailSender = new FakeMailSender();
        this.userRepository = new FakeUserRepository();
        this.postRepository = new FakePostRepository();
        this.certificationService = new CertificationService(mailSender);
        this.postService = new PostServiceImpl(postRepository, userRepository, clockHolder);
        UserServiceImpl userService = new UserServiceImpl(userRepository, certificationService, uuidHolder, clockHolder);

        userReadService = userService;
        userUpdateService = userService;
        userCreateService = userService;
        authenticationService = userService;

        this.userController = UserController.builder()
                .userReadService(userReadService)
                .userUpdateService(userUpdateService)
                .authenticationService(authenticationService)
                .build();

        this.userCreateController = new UserCreateController(userCreateService);

        this.postCreateController = new PostCreateController(postService);

        this.postController = new PostController(postService);
    }

    public static TestContainer create(ClockHolder clockHolder, UuidHolder uuidHolder) {
        return new TestContainer(clockHolder, uuidHolder);
    }
}
