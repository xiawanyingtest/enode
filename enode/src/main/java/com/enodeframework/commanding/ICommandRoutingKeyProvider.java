package com.enodeframework.commanding;

public interface ICommandRoutingKeyProvider {
    /**
     * Returns a routing key for the given command.
     */
    String getRoutingKey(ICommand command);
}
