package com.mikedeejay2.fastreload.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class to easily use Java reflection.
 *
 * @author Mikedeejay2
 */
public final class ReflectUtil
{
    /**
     * Get a field through reflection
     *
     * @param fieldName The name of the field to get
     * @param fromObj   The object to get the field from
     * @param fromClass The class to get the field from
     * @param toClass   The class to cast the result to
     * @param <T>       The return type, specified with toClass
     * @return The specified field
     * @throws NoSuchFieldException   If a field of that name doesn't exist
     * @throws IllegalAccessException Whether the specified field can't be accessed
     */
    public static <T> T getField(String fieldName, Object fromObj, Class<?> fromClass, Class<T> toClass)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = fromClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return toClass.cast(field.get(fromObj));
    }

    /**
     * Get a field through reflection
     *
     * @param fieldName The name of the field to get
     * @param fromObj   The object to get the field from
     * @param fromClass The class to get the field from
     * @return The specified field
     * @throws NoSuchFieldException   If a field of that name doesn't exist
     * @throws IllegalAccessException Whether the specified field can't be accessed
     */
    public static Object getField(String fieldName, Object fromObj, Class<?> fromClass)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = fromClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(fromObj);
    }

    /**
     * Invoke a method through reflection
     *
     * @param methodName     The name of the method to get
     * @param fromObj        The object to get the method from
     * @param fromClass      The class to get the method from
     * @param toClass        The class to cast the result to
     * @param parameterTypes A class array of parameter types of the method
     * @param args           An object array of arguments to pass to the method
     * @param <T>            The return type, specified with toClass
     * @return The return result of the method
     * @throws NoSuchMethodException     If a method of that name doesn't exist
     * @throws InvocationTargetException If the arguments don't line up with the parameter types
     * @throws IllegalAccessException    Whether the specified method can't be accessed
     */
    public static <T> T invokeMethod(String methodName, Object fromObj, Class<?> fromClass, Class<T> toClass, Class<?>[] parameterTypes, Object[] args)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = fromClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return toClass.cast(method.invoke(fromObj, args));
    }

    /**
     * Invoke a method through reflection
     *
     * @param methodName     The name of the method to get
     * @param fromObj        The object to get the method from
     * @param fromClass      The class to get the method from
     * @param parameterTypes A class array of parameter types of the method
     * @param args           An object array of arguments to pass to the method
     * @return The return result of the method
     * @throws NoSuchMethodException     If a method of that name doesn't exist
     * @throws InvocationTargetException If the arguments don't line up with the parameter types
     * @throws IllegalAccessException    Whether the specified method can't be accessed
     */
    public static Object invokeMethod(String methodName, Object fromObj, Class<?> fromClass, Class<?>[] parameterTypes, Object[] args)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = fromClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(fromObj, args);
    }

    /**
     * Invoke a constructor through reflection
     *
     * @param fromClass      The class to get the constructor from
     * @param toClass        The class to cast the constructed object to
     * @param parameterTypes A class array of parameter types of the constructor
     * @param args           An object array of arguments to pass to the constructor
     * @param <T>            The return type, specified with toClass
     * @return The return result of the constructor
     * @throws NoSuchMethodException     If a method of that name doesn't exist
     * @throws IllegalAccessException    Whether the specified method can't be accessed
     * @throws InvocationTargetException If the arguments don't line up with the parameter types
     * @throws InstantiationException    If the specified class can not be instantiated
     */
    public static <T> T construct(Class<?> fromClass, Class<T> toClass, Class<?>[] parameterTypes, Object[] args)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor constructor = fromClass.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return toClass.cast(constructor.newInstance(args));
    }

    /**
     * Invoke a constructor through reflection
     *
     * @param fromClass      The class to get the constructor from
     * @param parameterTypes A class array of parameter types of the constructor
     * @param args           An object array of arguments to pass to the constructor
     * @return The return result of the constructor
     * @throws NoSuchMethodException     If a method of that name doesn't exist
     * @throws IllegalAccessException    Whether the specified method can't be accessed
     * @throws InvocationTargetException If the arguments don't line up with the parameter types
     * @throws InstantiationException    If the specified class can not be instantiated
     */
    public static Object construct(Class<?> fromClass, Class<?>[] parameterTypes, Object[] args)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor constructor = fromClass.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
