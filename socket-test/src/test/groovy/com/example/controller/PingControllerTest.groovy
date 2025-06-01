package com.example.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PingControllerTest extends Specification {

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate restTemplate

    void "Ping API는 받은 count 값에 1을 더한 값을 리턴한다."() {
        given:
        def count = 1
        def uri = "http://localhost:${port}/api/ping/${count}"

        when:
        def responseEntity = restTemplate.getForEntity(uri, String)

        then:
        responseEntity.statusCode.value() == 200
        responseEntity.body == "pong : ${count + 1}"
    }
}
