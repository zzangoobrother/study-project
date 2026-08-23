package com.loopers.interfaces.api

/**
 * 요청 헤더 이름.
 *
 * 애노테이션 인자로 쓰이므로 const 여야 한다.
 * 컨트롤러마다 문자열을 따로 두면 세 곳에 같은 리터럴이 흩어지고, 그중 하나만 바뀌어도 아무도 눈치채지 못한다.
 */
object ApiHeaders {
    /** 요청 주체인 회원의 로그인 ID. 이 헤더는 식별만 하며 인증하지 않는다. */
    const val LOGIN_ID = "X-Loopers-LoginId"
}
