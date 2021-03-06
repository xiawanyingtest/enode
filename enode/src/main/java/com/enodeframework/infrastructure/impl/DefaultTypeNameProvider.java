package com.enodeframework.infrastructure.impl;

import com.enodeframework.infrastructure.ITypeNameProvider;
import com.enodeframework.infrastructure.WrappedRuntimeException;

public class DefaultTypeNameProvider implements ITypeNameProvider {

    @Override
    public String getTypeName(Class type) {
        return type.getName();
    }

    @Override
    public Class getType(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw new WrappedRuntimeException("ClassNotFound", e);
        }
    }
}
