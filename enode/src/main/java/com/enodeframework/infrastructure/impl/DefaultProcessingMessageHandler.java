package com.enodeframework.infrastructure.impl;

import com.enodeframework.common.io.AsyncTaskResult;
import com.enodeframework.common.io.IOHelper;
import com.enodeframework.common.io.Task;
import com.enodeframework.eventing.DomainEventStreamMessage;
import com.enodeframework.infrastructure.IMessage;
import com.enodeframework.infrastructure.IMessageDispatcher;
import com.enodeframework.infrastructure.IProcessingMessage;
import com.enodeframework.infrastructure.IProcessingMessageHandler;
import com.enodeframework.infrastructure.IPublishedVersionStore;
import com.enodeframework.infrastructure.ProcessingDomainEventStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

public class DefaultProcessingMessageHandler<X extends IProcessingMessage<X, Y>, Y extends IMessage> implements IProcessingMessageHandler<X, Y> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProcessingMessageHandler.class);

    private final String domainEventStreamMessageHandlerName = "DefaultEventProcessor";

    @Autowired
    private IMessageDispatcher dispatcher;

    @Autowired
    private IPublishedVersionStore publishedVersionStore;

    @Autowired
    private IOHelper ioHelper;

    @Override
    public CompletableFuture<Void> handleAsync(X processingMessage) {
        if (processingMessage instanceof ProcessingDomainEventStreamMessage) {
            return handleMessageAsync((ProcessingDomainEventStreamMessage) processingMessage, 0);
        }
        return dispatcher.dispatchMessageAsync(processingMessage.getMessage()).thenAccept(r -> processingMessage.complete());
    }

    public String getName() {
        return domainEventStreamMessageHandlerName;
    }

    private CompletableFuture<AsyncTaskResult> dispatchProcessingMessageAsync(ProcessingDomainEventStreamMessage processingMessage) {
        return dispatcher.dispatchMessagesAsync(processingMessage.getMessage().getEvents());
    }

    private CompletableFuture<Void> handleMessageAsync(ProcessingDomainEventStreamMessage processingMessage, int retryTimes) {
        DomainEventStreamMessage message = processingMessage.getMessage();
        ioHelper.tryAsyncActionRecursively("GetPublishedVersionAsync",
                () -> publishedVersionStore.getPublishedVersionAsync(getName(), message.aggregateRootTypeName(), message.aggregateRootStringId()),
                currentRetryTimes -> handleMessageAsync(processingMessage, currentRetryTimes),
                result ->
                {
                    Integer publishedVersion = result.getData();
                    if (publishedVersion + 1 == message.version()) {
                        doDispatchProcessingMessageAsync(processingMessage, 0);
                    } else if (publishedVersion + 1 < message.version()) {
                        logger.info("The sequence message cannot be process now as the version is not the next version, it will be handle later. contextInfo [aggregateRootId={},lastPublishedVersion={},messageVersion={}]", message.aggregateRootStringId(), publishedVersion, message.version());
                        processingMessage.addToWaitingList();
                    } else {
                        processingMessage.complete();
                    }
                },
                () -> String.format("sequence message [messageId:%s, messageType:%s, aggregateRootId:%s, aggregateRootVersion:%s]", message.id(), message.getClass().getName(), message.aggregateRootStringId(), message.version()),
                errorMessage ->
                        logger.error(String.format("Get published version has unknown exception, the code should not be run to here, errorMessage: %s", errorMessage)),
                retryTimes, true);
        return Task.CompletedTask;
    }

    private void doDispatchProcessingMessageAsync(ProcessingDomainEventStreamMessage processingMessage, int retryTimes) {
        ioHelper.tryAsyncActionRecursively("DispatchProcessingMessageAsync",
                () -> dispatchProcessingMessageAsync(processingMessage),
                currentRetryTimes -> doDispatchProcessingMessageAsync(processingMessage, currentRetryTimes),
                result -> updatePublishedVersionAsync(processingMessage, 0),
                () -> String.format("sequence message [messageId:%s, messageType:%s, aggregateRootId:%s, aggregateRootVersion:%d]", processingMessage.getMessage().id(), processingMessage.getMessage().getClass().getName(), processingMessage.getMessage().aggregateRootStringId(), processingMessage.getMessage().version()),
                errorMessage -> logger.error("Dispatching message has unknown exception, the code should not be run to here, errorMessage: {}", errorMessage),
                retryTimes, true);
    }

    private void updatePublishedVersionAsync(ProcessingDomainEventStreamMessage processingMessage, int retryTimes) {
        ioHelper.tryAsyncActionRecursively("UpdatePublishedVersionAsync",
                () -> publishedVersionStore.updatePublishedVersionAsync(getName(), processingMessage.getMessage().aggregateRootTypeName(), processingMessage.getMessage().aggregateRootStringId(), processingMessage.getMessage().version()),
                currentRetryTimes -> updatePublishedVersionAsync(processingMessage, currentRetryTimes),
                result -> processingMessage.complete(),
                () -> String.format("sequence message [messageId:%s, messageType:%s, aggregateRootId:%s, aggregateRootVersion:%d]", processingMessage.getMessage().id(), processingMessage.getMessage().getClass().getName(), processingMessage.getMessage().aggregateRootStringId(), processingMessage.getMessage().version()),
                errorMessage ->
                        logger.error(String.format("Update published version has unknown exception, the code should not be run to here, errorMessage: %s", errorMessage)),
                retryTimes, true);
    }
}
