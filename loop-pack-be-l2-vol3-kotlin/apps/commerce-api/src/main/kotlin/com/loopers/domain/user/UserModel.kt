package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 회원 엔티티.
 *
 * 평문 비밀번호가 저장되는 경로를 원천 차단하기 위해 생성자를 private 으로 막고,
 * 검증과 인코딩을 모두 수행하는 [create] 팩토리만 노출한다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, length = 10)
    var loginId: String = loginId
        protected set

    @Column(name = "password", nullable = false)
    var password: String = password
        protected set

    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    companion object {
        private val LOGIN_ID_REGEX = "^[a-zA-Z0-9]{1,10}$".toRegex()
        private val NAME_REGEX = "^[가-힣a-zA-Z]{1,20}$".toRegex()
        private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        private val BIRTH_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

        /**
         * 8~16자 / 영문·숫자·ASCII 특수문자만 허용 / 세 종류를 각각 1자 이상 포함.
         * 전방 탐색(lookahead)은 문자를 소비하지 않으므로 조건을 독립적으로 겹쳐 걸 수 있다.
         */
        private val PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*\\p{Punct})[A-Za-z\\d\\p{Punct}]{8,16}$".toRegex()

        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        fun create(
            loginId: String,
            rawPassword: String,
            name: String,
            birthDate: String,
            email: String,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            if (!LOGIN_ID_REGEX.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문 및 숫자 10자 이내여야 합니다.")
            }
            if (!NAME_REGEX.matches(name)) {
                throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문 20자 이내여야 합니다.")
            }
            if (!EMAIL_REGEX.matches(email)) {
                throw CoreException(ErrorType.BAD_REQUEST, "이메일은 xx@yy.zz 형식이어야 합니다.")
            }

            val parsedBirthDate = parseBirthDate(birthDate)
            validatePassword(rawPassword, parsedBirthDate)

            return UserModel(
                loginId = loginId,
                password = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = parsedBirthDate,
                email = email,
            )
        }

        private fun parseBirthDate(birthDate: String): LocalDate {
            if (!BIRTH_DATE_REGEX.matches(birthDate)) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.")
            }

            // ISO_LOCAL_DATE 는 STRICT 해석이라 1990-02-30 같은 값을 보정 없이 거부한다.
            val parsed = runCatching { LocalDate.parse(birthDate) }
                .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 생년월일입니다.") }

            if (parsed.isAfter(LocalDate.now())) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.")
            }
            return parsed
        }

        private fun validatePassword(rawPassword: String, birthDate: LocalDate) {
            if (!PASSWORD_REGEX.matches(rawPassword)) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 8~16자이며 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
                )
            }
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }
    }
}
