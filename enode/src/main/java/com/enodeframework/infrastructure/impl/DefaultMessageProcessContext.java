package com.enodeframework.infrastructure.impl;

import com.enodeframework.infrastructure.IMessageProcessContext;
import com.enodeframework.queue.IMessageContext;
import com.enodeframework.queue.QueueMessage;

public class DefaultMessageProcessContext implements IMessageProcessContext {

    protected final QueueMessage queueMessage;
    protected final IMessageContext messageContext;

    public DefaultMessageProcessContext(QueueMessage queueMessage, IMessageContext messageContext) {
        this.queueMessage = queueMessage;
        this.messageContext = messageContext;
    }

    @Override
    public void notifyMessageProcessed() {
    }
}
