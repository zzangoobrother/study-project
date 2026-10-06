package com.loopers.support.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class AfterCommitTest @Autowired constructor(
    private val transactionTemplate: TransactionTemplate,
) {
    @DisplayName("트랜잭션 밖에서 등록하면, ")
    @Nested
    inner class OutsideTransaction {
        @DisplayName("즉시 실행한다.")
        @Test
        fun runsImmediately() {
            // arrange
            val events = mutableListOf<String>()

            // act
            AfterCommit.run { events += "action" }

            // assert
            assertThat(events).containsExactly("action")
        }
    }

    @DisplayName("트랜잭션 안에서 등록하면, ")
    @Nested
    inner class InsideTransaction {
        @DisplayName("커밋이 끝난 뒤에 실행한다.")
        @Test
        fun runsAfterCommit() {
            // arrange
            val events = mutableListOf<String>()

            // act
            transactionTemplate.execute {
                AfterCommit.run { events += "action" }
                events += "end of transaction body"
            }

            // assert
            assertThat(events).containsExactly("end of transaction body", "action")
        }

        @DisplayName("롤백되면 실행하지 않는다.")
        @Test
        fun doesNotRun_whenRolledBack() {
            // arrange
            val events = mutableListOf<String>()

            // act
            transactionTemplate.execute { status ->
                AfterCommit.run { events += "action" }
                status.setRollbackOnly()
            }

            // assert
            assertThat(events).isEmpty()
        }
    }
}
