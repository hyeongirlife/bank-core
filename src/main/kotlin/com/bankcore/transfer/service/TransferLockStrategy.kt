package com.bankcore.transfer.service

enum class TransferLockStrategy {
    DISTRIBUTED,
    PESSIMISTIC;

    companion object {
        fun from(value: String): TransferLockStrategy {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 송금 락 전략입니다: $value")
        }
    }
}
