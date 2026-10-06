package com.loopers.support.transaction

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 트랜잭션 안이면 커밋 뒤에, 밖이면 즉시 실행한다. 롤백되면 실행하지 않는다.
 *
 * 캐시 삭제가 커밋 전에 일어나면, 삭제와 커밋 사이에 들어온 읽기가 옛 값을 DB 에서 읽어 다시 캐시에 넣는다.
 * 같은 삭제 코드가 Facade @Transactional 안(delete)과 밖(change) 양쪽에서 불리므로 호출부가 자기 위치를 신경 쓰지 않게 한다.
 * (2026-10-04 상품 캐시 설계 5.1 장)
 */
object AfterCommit {
    fun run(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }
}
