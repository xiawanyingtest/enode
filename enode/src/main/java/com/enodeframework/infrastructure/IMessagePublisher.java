package com.enodeframework.infrastructure;

import com.enodeframework.common.io.AsyncTaskResult;

import java.util.concurrent.CompletableFuture;

public interface IMessagePublisher<TMessage extends IMessage> {
    CompletableFuture<AsyncTaskResult> publishAsync(TMessage message);
}
