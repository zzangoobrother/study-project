package com.example.handler

import com.example.ChatStudyApplication
import com.example.dto.websocket.inbound.WriteMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import spock.lang.Specification

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = ChatStudyApplication, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketHandlerTest extends Specification{

    @LocalServerPort
    int port

    @Autowired
    ObjectMapper objectMapper

    def "Group Chat Basic Test" () {
        given:

        register("testuserA", "testpassA")
        register("testuserB", "testpassB")
        register("testuserC", "testpassC")
        def sessionIdA = login("testuserA", "testpassA")
        def sessionIdB = login("testuserB", "testpassB")
        def sessionIdC = login("testuserC", "testpassC")

        def (clientA, clientB, clientC) = [createClient(sessionIdA), createClient(sessionIdB), createClient(sessionIdC)]


        when:
        clientA.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new WriteMessage("clientA", "안녕하세요. A입니다."))))
        clientB.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new WriteMessage("clientB", "안녕하세요. B입니다."))))
        clientC.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new WriteMessage("clientC", "안녕하세요. C입니다."))))

        then:
        def resultA = clientA.queue.poll(1, TimeUnit.SECONDS) + clientA.queue.poll(1, TimeUnit.SECONDS)
        def resultB = clientB.queue.poll(1, TimeUnit.SECONDS) + clientB.queue.poll(1, TimeUnit.SECONDS)
        def resultC = clientC.queue.poll(1, TimeUnit.SECONDS) + clientC.queue.poll(1, TimeUnit.SECONDS)
        resultA.contains("clientB") && resultA.contains("clientC")
        resultB.contains("clientA") && resultB.contains("clientC")
        resultC.contains("clientA") && resultC.contains("clientB")

        and:
        clientA.queue.isEmpty()
        clientB.queue.isEmpty()
        clientC.queue.isEmpty()

        cleanup:
        unregister(sessionIdA)
        unregister(sessionIdB)
        unregister(sessionIdC)
        clientA.session?.close()
        clientB.session?.close()
        clientC.session?.close()
    }

    def register(String username, String password) {
        def url = "http://localhost:${port}/api/v1/auth/register"
        def headers = new HttpHeaders(["Content-Type" : "application/json"])
        def jsonBody = objectMapper.writeValueAsString([username : username, password : password])
        def httpEntity = new HttpEntity(jsonBody, headers)
        try {
            new RestTemplate().exchange(url, HttpMethod.POST, httpEntity, String)
        } catch (Exception ex) {
        }
    }

    def unregister(String sessionId) {
        def url = "http://localhost:${port}/api/v1/auth/unregister"
        def headers = new HttpHeaders()
        headers.add("Content-Type", "application/json")
        headers.add("Cookie", "SESSION=${sessionId}")
        def httpEntity = new HttpEntity(headers)
        def responseEntity = new RestTemplate().exchange(url, HttpMethod.POST, httpEntity, String)
        responseEntity.body
    }

    def login(String username, String password) {
        def url = "http://localhost:${port}/api/v1/auth/login"
        def headers = new HttpHeaders(["Content-Type" : "application/json"])
        def jsonBody = objectMapper.writeValueAsString([username : username, password : password])
        def httpEntity = new HttpEntity(jsonBody, headers)
        def responseEntity = new RestTemplate().exchange(url, HttpMethod.POST, httpEntity, String)
        def sessionId = responseEntity.body
        sessionId
    }

    def createClient(String sessionId) {
        def url = "ws://localhost:${port}/ws/v1/message"
        BlockingQueue<String> blockingQueue = new ArrayBlockingQueue<>(5)
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders()
        headers.add("Cookie", "SESSION=${sessionId}")
        StandardWebSocketClient client = new StandardWebSocketClient()
        WebSocketSession webSocketSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                blockingQueue.put(message.payload)
            }
        }, headers, new URI(url)).get()

        [queue: blockingQueue, session: webSocketSession]
    }
}
