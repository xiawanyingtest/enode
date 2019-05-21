package com.enodeframework.infrastructure.impl;

import com.enodeframework.annotation.Command;
import com.enodeframework.annotation.Event;
import com.enodeframework.annotation.Priority;
import com.enodeframework.annotation.Subscribe;
import com.enodeframework.common.Constants;
import com.enodeframework.common.container.IObjectContainer;
import com.enodeframework.infrastructure.IAssemblyInitializer;
import com.enodeframework.infrastructure.IObjectProxy;
import com.enodeframework.infrastructure.MessageHandlerData;
import com.enodeframework.infrastructure.MethodInvocation;
import com.enodeframework.infrastructure.WrappedRuntimeException;
import org.reflections.ReflectionUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractHandlerProvider<TKey, THandlerProxyInterface extends IObjectProxy & MethodInvocation, THandlerSource> implements IAssemblyInitializer {
    private Map<TKey, List<THandlerProxyInterface>> handlerDict = new HashMap<>();

    private Map<TKey, MessageHandlerData<THandlerProxyInterface>> messageHandlerDict = new HashMap<>();

    private MethodHandles.Lookup lookup = MethodHandles.lookup();

    protected abstract TKey getKey(Method method);

    protected abstract Class<? extends THandlerProxyInterface> getHandlerProxyImplementationType();

    protected abstract boolean isHandlerSourceMatchKey(THandlerSource handlerSource, TKey key);

    protected abstract boolean isHandleMethodMatch(Method method);

    protected abstract IObjectContainer getObjectContainer();

    @Override
    public void initialize(Set<Class<?>> componentTypes) {
        componentTypes.stream().filter(this::isHandlerType).forEach(this::registerHandler);
        initializeHandlerPriority();
    }

    public List<MessageHandlerData<THandlerProxyInterface>> getHandlers(THandlerSource source) {
        List<MessageHandlerData<THandlerProxyInterface>> handlerDataList = new ArrayList<>();

        messageHandlerDict.keySet().stream()
                .filter(key -> isHandlerSourceMatchKey(source, key))
                .forEach(key -> handlerDataList.add(messageHandlerDict.get(key)));

        return handlerDataList;
    }

    private void initializeHandlerPriority() {
        handlerDict.forEach((key, handlers) -> {

            MessageHandlerData<THandlerProxyInterface> handlerData = new MessageHandlerData<>();
            List<THandlerProxyInterface> listHandlers = new ArrayList<>();
            Map<THandlerProxyInterface, Integer> queueHandlerDict = new HashMap<>();

            handlers.forEach(handler -> {
                int priority = getHandleMethodPriority(handler);

                if (priority == 0) {
                    listHandlers.add(handler);
                } else {
                    queueHandlerDict.put(handler, priority);
                }
            });

            handlerData.AllHandlers = handlers;
            handlerData.ListHandlers = listHandlers;
            handlerData.QueuedHandlers = queueHandlerDict.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue)).map(x -> x.getKey()).collect(Collectors.toList());

            messageHandlerDict.put(key, handlerData);
        });
    }

    private int getHandleMethodPriority(THandlerProxyInterface handler) {
        Method method = handler.getMethod();
        if (Constants.COMMAND_HANDLE_METHOD.equals(method.getName())) {
            int priority = 0;
            Priority methodPriority = method.getAnnotation(Priority.class);
            if (methodPriority != null) {
                priority = methodPriority.value();
            }

            if (priority == 0) {
                Priority classPriority = handler.getInnerObject().getClass().getAnnotation(Priority.class);
                if (classPriority != null) {
                    priority = classPriority.value();
                }
            }

            return priority;
        } else {
            return 0;
        }
    }

    private boolean isHandlerType(Class type) {
        if (type == null) {
            return false;
        }
        if (type.isInterface()) {
            return false;
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        if (!containsHandleType(type)) {
            return false;
        }
        return true;
    }

    private boolean containsHandleType(Class type) {
        return type.isAnnotationPresent(Command.class) || type.isAnnotationPresent(Event.class);
    }

    /**
     * @param method
     * @return
     */
    protected boolean isMethodAnnotationSubscribe(Method method) {
        return method.isAnnotationPresent(Subscribe.class);
    }

    private void registerHandler(Class handlerType) {

        Set<Method> handleMethods = ReflectionUtils.getMethods(handlerType, this::isHandleMethodMatch);

        handleMethods.forEach(method -> {
            try {
                //反射Method转换为MethodHandle,提高效率
                MethodHandle handleMethod = lookup.findVirtual(handlerType, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
                TKey key = getKey(method);
                List<THandlerProxyInterface> handlers = handlerDict.computeIfAbsent(key, k -> new ArrayList<>());
                IObjectContainer objectContainer = getObjectContainer();
                if (objectContainer == null) {
                    throw new NullPointerException("IObjectContainer is null");
                }
                // prototype
                THandlerProxyInterface handlerProxy = objectContainer.resolve(getHandlerProxyImplementationType());
                if (handlerProxy == null) {
                    throw new RuntimeException("THandlerProxyInterface is null, " + getHandlerProxyImplementationType().getName());
                }
                handlerProxy.setHandlerType(handlerType);
                handlerProxy.setMethod(method);
                handlerProxy.setMethodHandle(handleMethod);
                handlers.add(handlerProxy);
            } catch (Exception e) {
                throw new WrappedRuntimeException(e);
            }
        });
    }
}
