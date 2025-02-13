package com.example.before.fcm;

import com.example.after.fcm.CustomThreadManager;
import com.example.config.properties.FcmProperties;
import com.example.dto.FcmMulticastMessage;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class FcmClient {

    private static final String AUTH_URL = "firebase-auth-url";
    private FirebaseApp firebaseApp;
    private final FcmProperties fcmProperties;

    public FcmClient(FcmProperties fcmProperties) {
        this.fcmProperties = fcmProperties;
    }

    @PostConstruct
    void getFirebaseAccessToken() {
        try {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(
                            GoogleCredentials
                                    .fromStream(new ClassPathResource(fcmProperties.serviceAccountFile()).getInputStream())
                                    .createScoped(Arrays.asList(AUTH_URL))
                    )
                    .setThreadManager(new CustomThreadManager())
                    .build();

            firebaseApp = FirebaseApp.initializeApp(options, "before");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    // 1 : N 푸쉬 전송
    public void send(FcmMulticastMessage fcmMulticastMessage) {
        List<String> tokens = fcmMulticastMessage.token();
        List<List<String>> tokenPartition = Lists.partition(tokens, 500);
        List<MulticastMessage> multicastMessages = tokenPartition.stream()
                .map(it -> createMulticastMessage(it, fcmMulticastMessage.notification().title(), fcmMulticastMessage.notification().body(), fcmMulticastMessage.notification().image(), fcmMulticastMessage.options()))
                .toList();

        multicastMessages.forEach(it -> FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticastAsync(it));
    }

    private MulticastMessage createMulticastMessage(List<String> tokens, String title, String content, String image, Map<String, String> options) {
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(content)
                        .setImage(image)
                        .build())
                .addAllTokens(tokens)
                .build();

        if (!CollectionUtils.isEmpty(options)) {
            message = MulticastMessage.builder()
                    .putAllData(options)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(content)
                            .setImage(image)
                            .build())
                    .addAllTokens(tokens)
                    .build();
        }

        return message;
    }
}
