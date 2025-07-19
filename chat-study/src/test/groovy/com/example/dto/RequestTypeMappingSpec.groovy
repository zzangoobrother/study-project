package com.example.dto

import com.example.dto.kafka.*
import com.example.util.JsonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class RequestTypeMappingSpec extends Specification {

    JsonUtil jsonUtil = new JsonUtil(new ObjectMapper());

    def "DTO 형식의 JSON 문자열을 해당 타입의 DTO로 변환할 수 있다."() {
        given:
        String jsonBody = payload

        when:
        RecordInterface record = jsonUtil.fromJson(jsonBody, RecordInterface).get()

        then:
        record.getClass() == expectedClass
        validate(record)

        where:
        payload                                                                             | expectedClass                 | validate
        '{"type" : "FETCH_USER_INVITECODE_REQUEST"}'                                        | FetchUserInvitecodeRequestRecord    | {req -> (req as FetchUserInvitecodeRequestRecord).type() == 'FETCH_USER_INVITECODE_REQUEST'}
        '{"type" : "FETCH_CONNECTIONS_REQUEST", "status" : "ACCEPTED"}'                     | FetchConnectionsRequestRecord       | {req -> (req as FetchConnectionsRequestRecord).status().name() == 'ACCEPTED'}
        '{"type" : "INVITE_REQUEST", "userInviteCode" : "TestInviteCode123"}'               | InviteRequestRecord                 | {req -> (req as InviteRequestRecord).userInviteCode().code() == "TestInviteCode123"}
        '{"type" : "ACCEPT_REQUEST", "username" : "testuser"}'                              | AcceptRequestRecord                 | { req -> (req as AcceptRequestRecord).username() == "testuser"}
        '{"type" : "REJECT_REQUEST", "username" : "testuser"}'                              | RejectRequestRecord                 | { req -> (req as RejectRequestRecord).username() == "testuser"}
        '{"type" : "DISCONNECT_REQUEST", "username" : "testuser"}'                          | DisconnectRequestRecord             | { req -> (req as DisconnectRequestRecord).username() == "testuser"}
        '{"type" : "WRITE_MESSAGE", "content" : "test message"}'                            | WriteMessageRecord                  | { req -> (req as WriteMessageRecord).content() == "test message"}
    }
}
