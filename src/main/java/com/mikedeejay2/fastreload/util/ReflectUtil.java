package com.mikedeejay2.fastreload.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ReflectUtil
{
    public static <T> T getField(String fieldName, Object fromObj, Class<?> fromClass, Class<T> toClass) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = fromClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return toClass.cast(field.get(fromObj));
    }

    public static Object getField(String fieldName, Object fromObj, Class<?> fromClass) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = fromClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(fromObj);
    }

    public static <T> T invokeMethod(String fieldName, Object fromObj, Class<?> fromClass, Class<T> toClass, Class<?>[] parameterTypes, Object[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Method method = fromClass.getDeclaredMethod(fieldName, parameterTypes);
        method.setAccessible(true);
        return toClass.cast(method.invoke(fromObj, args));
    }

    public static Object invokeMethod(String fieldName, Object fromObj, Class<?> fromClass, Class<?>[] parameterTypes, Object[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Method method = fromClass.getDeclaredMethod(fieldName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(fromObj, args);
    }
}
