package com.enodeframework.infrastructure;

public interface IMessageProcessor<X extends IProcessingMessage<X, Y>, Y extends IMessage> {
    void process(X processingMessage);

    void start();

    void stop();
}
