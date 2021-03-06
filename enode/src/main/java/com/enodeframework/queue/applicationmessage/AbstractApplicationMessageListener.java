package com.enodeframework.queue.applicationmessage;

import com.enodeframework.common.serializing.IJsonSerializer;
import com.enodeframework.infrastructure.IApplicationMessage;
import com.enodeframework.infrastructure.IMessageProcessor;
import com.enodeframework.infrastructure.ITypeNameProvider;
import com.enodeframework.infrastructure.ProcessingApplicationMessage;
import com.enodeframework.infrastructure.impl.DefaultMessageProcessContext;
import com.enodeframework.queue.IMessageContext;
import com.enodeframework.queue.IMessageHandler;
import com.enodeframework.queue.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractApplicationMessageListener implements IMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractApplicationMessageListener.class);

    @Autowired
    protected IJsonSerializer jsonSerializer;

    @Autowired
    protected ITypeNameProvider typeNameProvider;

    @Autowired
    protected IMessageProcessor<ProcessingApplicationMessage, IApplicationMessage> applicationMessageProcessor;

    @Override
    public void handle(QueueMessage queueMessage, IMessageContext context) {
        String msg = queueMessage.getBody();
        ApplicationDataMessage appDataMessage = jsonSerializer.deserialize(msg, ApplicationDataMessage.class);
        Class applicationMessageType = typeNameProvider.getType(appDataMessage.getApplicationMessageType());
        IApplicationMessage message = (IApplicationMessage) jsonSerializer.deserialize(appDataMessage.getApplicationMessageData(), applicationMessageType);
        DefaultMessageProcessContext processContext = new DefaultMessageProcessContext(queueMessage, context);
        ProcessingApplicationMessage processingMessage = new ProcessingApplicationMessage(message, processContext);
        logger.info("ENode application message received, messageId: {}, routingKey: {}", message.id(), message.getRoutingKey());
        applicationMessageProcessor.process(processingMessage);
    }
}
