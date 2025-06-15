package com.example.integration

import com.example.ChatStudyApplication
import com.example.dto.domain.UserId
import com.example.repository.UserConnectionRepository
import com.example.repository.UserRepository
import com.example.service.UserConnectionLimitService
import com.example.service.UserConnectionService
import com.example.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import static java.util.Collections.*

@SpringBootTest(classes = ChatStudyApplication)
class UserConnectionServiceSpec extends Specification {

    @Autowired
    UserService userService

    @Autowired
    UserConnectionService userConnectionService

    @Autowired
    UserConnectionLimitService userConnectionLimitService

    @Autowired
    UserRepository userRepository

    @Autowired
    UserConnectionRepository userConnectionRepository

    def '연결 요청 수락은 연결 제한 수를 넘을 수 없다.'() {
        given:
        userConnectionLimitService.setLimitConnection(10)
        (0..19).collect{userService.addUser("testuser${it}", "testpass${it}") }
        def userIdA = userService.getUserId("testuser0").get()
        def inviteCodeA = userService.getInviteCode(userIdA).get()

        (1..9).collect{
            userConnectionService.invite(userService.getUserId("testuser${it}").get(), inviteCodeA)
            userConnectionService.accept(userIdA, "testUser${it}")
        }

        def inviteCodes = (10..19).collect {
            userService.getInviteCode(userService.getUserId("testuser${it}").get()).get()
        }
        inviteCodes.each {userConnectionService.invite(userIdA, it)}

        List<Optional<UserId>> result = synchronizedList(new ArrayList<Optional<UserId>>())

        when:
        def threads = (10..19).collect {
            idx -> Thread.start {
                def userId = userService.getUserId("testuser${idx}")
                result << userConnectionService.accept(userId.get(), "testuser0").getFirst()
            }
        }

        threads*.join()

        then:
        result.count { it.isPresent() } == 1

        cleanup:
        (0..19).each {
            def userId = userService.getUserId("testuser${it}").get()
            userRepository.deleteById(userId.id())
        }
    }
}
