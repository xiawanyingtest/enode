package com.enodeframework.commanding;

import com.enodeframework.infrastructure.MessageHandlerData;

import java.util.List;

/**
 * Represents a provider to provide the command handlers.
 */
public interface ICommandHandlerProvider {
    /**
     * Get all the handlers for the given command type.
     */
    List<MessageHandlerData<ICommandHandlerProxy>> getHandlers(Class commandType);
}
