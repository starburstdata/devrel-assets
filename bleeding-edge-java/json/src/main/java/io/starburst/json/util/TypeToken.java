package io.starburst.json.util;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

public interface TypeToken<T>
{
    default Type type()
    {
        Type[] genericInterfaces = getClass().getGenericInterfaces();
        if (genericInterfaces.length == 1) {
            if ((genericInterfaces[0] instanceof ParameterizedType parameterizedType) && (parameterizedType.getActualTypeArguments().length == 1)) {
                return parameterizedType.getActualTypeArguments()[0];
            }
        }
        throw new IllegalArgumentException("Badly specified type");
    }

    static Class<?> getRawType(Type type)
    {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return Array.newInstance(getRawType(componentType), 0).getClass();
        }
        if ((type instanceof TypeVariable) || (type instanceof WildcardType)) {
            return Object.class;
        }
        throw new RuntimeException();   // TODO
    }
}
