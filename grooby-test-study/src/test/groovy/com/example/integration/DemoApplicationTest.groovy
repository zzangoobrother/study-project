package com.example.integration

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class DemoApplicationTest extends Specification {

    def "contextLoads"() {
        expect:
        true
    }
}
