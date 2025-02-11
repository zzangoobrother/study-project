package com.example.after.fcm;

import com.example.dto.FcmMulticastMessage;
import com.google.firebase.messaging.BatchResponse;

public interface FcmClient {
    BatchResponse send(FcmMulticastMessage fcmMulticastMessage);
}
