package com.enodeframework.commanding.impl;

import com.enodeframework.commanding.ICommand;
import com.enodeframework.commanding.ICommandAsyncHandlerProxy;
import com.enodeframework.common.container.IObjectContainer;
import com.enodeframework.common.io.AsyncTaskResult;
import com.enodeframework.infrastructure.IApplicationMessage;
import com.enodeframework.infrastructure.WrappedRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public class CommandAsyncHandlerProxy implements ICommandAsyncHandlerProxy {

    @Autowired
    private IObjectContainer objectContainer;

    private Class handlerType;

    private Object commandHandler;

    private MethodHandle methodHandle;

    private Method method;

    @Override
    public CompletableFuture<AsyncTaskResult<IApplicationMessage>> handleAsync(ICommand command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (AsyncTaskResult<IApplicationMessage>) methodHandle.invoke(getInnerObject(), command);
            } catch (Throwable e) {
                throw new WrappedRuntimeException(e);
            }
        });
    }

    @Override
    public Object getInnerObject() {
        if (commandHandler != null) {
            return commandHandler;
        }
        commandHandler = objectContainer.resolve(handlerType);
        return commandHandler;
    }

    @Override
    public void setHandlerType(Class handlerType) {
        this.handlerType = handlerType;
    }

    @Override
    public void setMethodHandle(MethodHandle methodHandle) {
        this.methodHandle = methodHandle;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public void setMethod(Method method) {
        this.method = method;
    }
}
