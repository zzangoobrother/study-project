package com.example.handler

import com.example.ChatStudyApplication
import com.example.dto.Message
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import spock.lang.Specification

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = ChatStudyApplication, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessageHandlerTest extends Specification{

    @LocalServerPort
    private int port

    private ObjectMapper objectMapper = new ObjectMapper()

    def "Direct Chat Basic Test" () {
        given:
        def url = "ws://localhost:${port}/ws/v1/message"
        BlockingQueue<String> leftQueue = new ArrayBlockingQueue<>(1)
        BlockingQueue<String> rightQueue = new ArrayBlockingQueue<>(1)

        StandardWebSocketClient leftClient = new StandardWebSocketClient()
        WebSocketSession leftWebSocketSession = leftClient.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                leftQueue.put(message.payload)
            }
        }, url).get()

        StandardWebSocketClient rightClient = new StandardWebSocketClient()
        WebSocketSession rightWebSocketSession = rightClient.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                rightQueue.put(message.payload)
            }
        }, url).get()

        when:
        leftWebSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(new Message("안녕하세요."))))
        rightWebSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(new Message("Hello."))))

        then:
        rightQueue.poll(1, TimeUnit.SECONDS).contains("안녕하세요")

        and:
        leftQueue.poll(1, TimeUnit.SECONDS).contains("Hello.")

        cleanup:
        leftWebSocketSession?.close()
        rightWebSocketSession?.close()
    }
}
