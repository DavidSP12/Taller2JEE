package com.taller2jee.logic.saga;

public class DistributedTransactionError extends RuntimeException {
    public DistributedTransactionError(String message, Throwable cause) {
        super(message, cause);
    }
}
