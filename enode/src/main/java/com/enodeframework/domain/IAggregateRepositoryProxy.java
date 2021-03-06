package com.enodeframework.domain;

import com.enodeframework.infrastructure.IObjectProxy;

import java.util.concurrent.CompletableFuture;

public interface IAggregateRepositoryProxy extends IObjectProxy {
    CompletableFuture<IAggregateRoot> getAsync(String aggregateRootId);
}
