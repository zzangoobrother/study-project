package com.loopers.infrastructure.user

import com.loopers.domain.user.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * SHA-256 + 랜덤 salt 기반 비밀번호 인코더.
 *
 * 저장 형태는 "Base64(salt):Base64(hash)" 이며, salt 를 결과 문자열에 함께 담아
 * 별도 컬럼 없이 검증할 수 있도록 한다.
 *
 * 주의: SHA-256 은 연산이 빨라 무차별 대입에 취약하고 work factor 개념이 없다.
 * 학습 목적으로 salt/해싱 동작을 직접 드러내기 위해 선택했으며,
 * 실서비스에서는 BCrypt 또는 PBKDF2 로 교체해야 한다.
 */
@Component
class Sha256PasswordEncoder : PasswordEncoder {
    override fun encode(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SECURE_RANDOM.nextBytes(it) }
        val hash = hash(salt, rawPassword)
        return "${ENCODER.encodeToString(salt)}$DELIMITER${ENCODER.encodeToString(hash)}"
    }

    override fun matches(rawPassword: String, encodedPassword: String): Boolean {
        val parts = encodedPassword.split(DELIMITER)
        if (parts.size != 2) return false

        val decoded = runCatching { DECODER.decode(parts[0]) to DECODER.decode(parts[1]) }
            .getOrElse { return false }
        val (salt, expectedHash) = decoded

        // 타이밍 공격 표면을 줄이기 위해 상수 시간 비교를 사용한다.
        return MessageDigest.isEqual(hash(salt, rawPassword), expectedHash)
    }

    private fun hash(salt: ByteArray, rawPassword: String): ByteArray =
        MessageDigest.getInstance(ALGORITHM).run {
            update(salt)
            digest(rawPassword.toByteArray(Charsets.UTF_8))
        }

    companion object {
        private const val ALGORITHM = "SHA-256"
        private const val DELIMITER = ":"
        private const val SALT_LENGTH = 16

        private val SECURE_RANDOM = SecureRandom()
        private val ENCODER: Base64.Encoder = Base64.getEncoder()
        private val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
