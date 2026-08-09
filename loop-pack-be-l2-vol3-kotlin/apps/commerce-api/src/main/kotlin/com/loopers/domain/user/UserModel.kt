package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.format.DateTimeFormatter

/**
 * 회원 엔티티.
 *
 * 필드별 검증은 각 값 객체가 소유하므로, 이 클래스에는 여러 값에 걸친 규칙만 남는다.
 * 평문 비밀번호가 저장되는 경로는 [EncodedPassword] 타입으로 차단된다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: LoginId,
    password: EncodedPassword,
    name: UserName,
    birthDate: BirthDate,
    email: Email,
) : BaseEntity() {
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "login_id", nullable = false, length = 10))
    var loginId: LoginId = loginId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "password", nullable = false))
    var password: EncodedPassword = password
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = 20))
    var name: UserName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "birth_date", nullable = false))
    var birthDate: BirthDate = birthDate
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "email", nullable = false, length = 254))
    var email: Email = email
        protected set

    /**
     * 비밀번호를 교체한다.
     *
     * "바꿔도 되는가"(기존 비밀번호 일치)와 "무엇으로 바꿀 수 있는가"(기존과 다를 것, 생년월일 불포함)가
     * 모두 이 애그리거트의 상태(password, birthDate)에 의존하므로 판정을 여기서 한다.
     *
     * 검사 순서에는 두 구간이 있다.
     * 요청 데이터만으로 판정할 수 있는 것을 먼저 보고(400), 그다음 자격 증명을 검증하며(401),
     * 저장된 상태에 의존하는 정책은 인증 뒤에 둔다(400).
     * 근거는 설계 문서 6.3 장과 9.5 장에 있다.
     */
    fun changePassword(
        currentPassword: RawPassword,
        newPassword: RawPassword,
        passwordEncoder: PasswordEncoder,
    ) {
        // 두 값 모두 요청에서 온 것이므로 이 판정은 저장된 상태를 전혀 드러내지 않는다.
        // 인증보다 앞에 두어야 400/401 차이가 "기존 비밀번호를 맞혔다" 는 확증이 되지 않는다.
        if (currentPassword == newPassword) {
            throw CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호와 새 비밀번호가 같습니다.")
        }

        if (!passwordEncoder.matches(currentPassword, password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, INVALID_CREDENTIAL_MESSAGE)
        }

        // 저장된 birthDate 에 의존하므로 반드시 인증 뒤에 남아야 한다.
        // 앞으로 옮기면 틀린 비밀번호로도 피해자의 생년월일을 맞혀 볼 수 있는 반대 방향의 유출이 생긴다.
        validateBirthDateNotIncluded(newPassword, birthDate)

        password = passwordEncoder.encode(newPassword)
    }

    companion object {
        /**
         * 자격 증명 검증 실패 시의 단일 문구.
         *
         * 미가입 / 소프트 삭제 / 비밀번호 불일치를 구분해 알려주지 않기 위해 UserService 도 이 상수를 참조한다.
         * 응답 차이로 로그인 ID 의 존재 여부를 유추할 수 있는 경로를 남기지 않는다.
         */
        const val INVALID_CREDENTIAL_MESSAGE = "로그인 ID 또는 비밀번호가 올바르지 않습니다."

        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        /**
         * 단일 값으로 판정할 수 없는 규칙이라 애그리거트 루트가 소유한다.
         * 가입(create)과 비밀번호 변경(changePassword)이 같은 규칙을 쓰도록 한 곳에 둔다.
         */
        private fun validateBirthDateNotIncluded(rawPassword: RawPassword, birthDate: BirthDate) {
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.value.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }

        fun create(
            loginId: LoginId,
            rawPassword: RawPassword,
            name: UserName,
            birthDate: BirthDate,
            email: Email,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            validateBirthDateNotIncluded(rawPassword, birthDate)

            return UserModel(
                loginId = loginId,
                password = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }
}
