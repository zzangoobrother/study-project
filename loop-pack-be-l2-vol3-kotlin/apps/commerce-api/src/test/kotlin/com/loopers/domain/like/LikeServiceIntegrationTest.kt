package com.loopers.domain.like

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class LikeServiceIntegrationTest @Autowired constructor(
    private val likeService: LikeService,
    private val productLikeRepository: ProductLikeRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val USER_ID = 1L
        private const val PRODUCT_ID = 2L
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun findLike(): ProductLikeModel? =
        productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID)

    @DisplayName("좋아요를 걸 때, ")
    @Nested
    inner class Like {
        @DisplayName("행이 없으면, 저장되고 전이했다고 보고한다.")
        @Test
        fun savesAndReportsTransition_whenRowDoesNotExist() {
            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val found = findLike()
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(found).isNotNull() },
                { assertThat(found?.deletedAt).isNull() },
            )
        }

        @DisplayName("취소된 행이 있으면, 새 행을 만들지 않고 되살리며 전이했다고 보고한다.")
        @Test
        fun restoresExistingRow_whenRowIsSoftDeleted() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            val originalId = findLike()!!.id
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val found = findLike()
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(found?.deletedAt).isNull() },
                // 새 행이 생기지 않았다는 것이 부활 설계의 요체다. INSERT 로 처리하면 유니크 제약에 걸린다.
                { assertThat(found?.id).isEqualTo(originalId) },
            )
        }

        @DisplayName("이미 좋아요 상태면, 아무것도 바꾸지 않고 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenAlreadyLiked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            val before = findLike()!!

            // act
            val transitioned = likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            val after = findLike()!!
            assertAll(
                { assertThat(transitioned).isFalse() },
                { assertThat(after.id).isEqualTo(before.id) },
                { assertThat(after.updatedAt).isEqualTo(before.updatedAt) },
            )
        }

        /**
         * updatedAt 을 SET 절에서 빠뜨리면 이 단언이 실패한다.
         * JPQL 벌크 UPDATE 는 PreUpdate 콜백을 타지 않으므로 손으로 써야 한다.
         */
        @DisplayName("되살릴 때, updatedAt 이 갱신된다.")
        @Test
        fun refreshesUpdatedAt_whenRestoring() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)
            val beforeRestore = findLike()!!.updatedAt

            // act
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertThat(findLike()!!.updatedAt).isAfter(beforeRestore)
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요 상태면, deletedAt 이 채워지고 전이했다고 보고한다.")
        @Test
        fun softDeletesAndReportsTransition_whenLiked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isTrue() },
                { assertThat(findLike()?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("이미 취소된 상태면, 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenAlreadyUnliked() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)
            val before = findLike()!!.deletedAt

            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isFalse() },
                // 취소 시각이 덮어씌워지지 않아야 한다. 덮어씌워지면 조건절이 빠진 것이다.
                { assertThat(findLike()?.deletedAt).isEqualTo(before) },
            )
        }

        @DisplayName("행이 아예 없으면, 예외 없이 전이하지 않았다고 보고한다.")
        @Test
        fun reportsNoTransition_whenRowDoesNotExist() {
            // act
            val transitioned = likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(transitioned).isFalse() },
                { assertThat(findLike()).isNull() },
            )
        }
    }

    @DisplayName("좋아요 행을 조회할 때, ")
    @Nested
    inner class FindIncludingDeleted {
        @DisplayName("취소된 행도 반환한다.")
        @Test
        fun returnsSoftDeletedRow() {
            // arrange
            likeService.like(userId = USER_ID, productId = PRODUCT_ID)
            likeService.unlike(userId = USER_ID, productId = PRODUCT_ID)

            // act
            val found = productLikeRepository.findIncludingDeleted(userId = USER_ID, productId = PRODUCT_ID)

            // assert
            assertAll(
                { assertThat(found).isNotNull() },
                { assertThat(found?.deletedAt).isNotNull() },
                { assertThat(found?.deletedAt).isBefore(ZonedDateTime.now()) },
            )
        }
    }
}
