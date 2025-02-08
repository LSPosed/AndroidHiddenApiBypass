package org.lsposed.lspass;

import android.os.Build;
import android.util.Log;
import android.util.Property;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import dalvik.system.VMRuntime;

@RequiresApi(Build.VERSION_CODES.P)
public final class LSPass {
    private static final String TAG = "LSPass";
    private static final Property<Class, Method[]> methods;
    private static final Property<Class, Constructor[]> constructors;
    private static final Property<Class, Field[]> fields;
    private static final Set<String> signaturePrefixes = new HashSet<>();

    static {
        methods = Property.of(Class.class, Method[].class, "DeclaredMethods");
        constructors = Property.of(Class.class, Constructor[].class, "DeclaredConstructors");
        fields = Property.of(Class.class, Field[].class, "DeclaredFields");
    }

    private static boolean checkArgsForInvokeMethod(Class<?>[] params, Object[] args) {
        if (params.length != args.length) return false;
        for (int i = 0; i < params.length; ++i) {
            if (params[i].isPrimitive()) {
                if (params[i] == int.class && !(args[i] instanceof Integer)) return false;
                else if (params[i] == byte.class && !(args[i] instanceof Byte)) return false;
                else if (params[i] == char.class && !(args[i] instanceof Character)) return false;
                else if (params[i] == boolean.class && !(args[i] instanceof Boolean)) return false;
                else if (params[i] == double.class && !(args[i] instanceof Double)) return false;
                else if (params[i] == float.class && !(args[i] instanceof Float)) return false;
                else if (params[i] == long.class && !(args[i] instanceof Long)) return false;
                else if (params[i] == short.class && !(args[i] instanceof Short)) return false;
            } else if (args[i] != null && !params[i].isInstance(args[i])) return false;
        }
        return true;
    }

    /**
     * get declared methods of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods
     * @return array of declared methods of {@code clazz}
     */
    public static Method[] getDeclaredMethods(@NonNull Class<?> clazz) {
        return methods.get(clazz);
    }

    /**
     * get declared constructors of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared constructors
     * @return array of declared constructors of {@code clazz}
     */
    public static Constructor<?>[] getDeclaredConstructors(@NonNull Class<?> clazz) {
        return constructors.get(clazz);
    }

    /**
     * get declared fields of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods
     * @return array of declared fields of {@code clazz}
     */
    public static Field[] getDeclaredFields(@NonNull Class<?> clazz) {
        return fields.get(clazz);
    }

    /**
     * get a restrict method named {@code methodName} of the given class {@code clazz} with argument types {@code parameterTypes}
     *
     * @param clazz          the class where the expected method declares
     * @param methodName     the expected method's name
     * @param parameterTypes argument types of the expected method with name {@code methodName}
     * @return the found method
     * @throws NoSuchMethodException when no method matches the given parameters
     * @see Class#getDeclaredMethod(String, Class[])
     */
    @NonNull
    public static Method getDeclaredMethod(@NonNull Class<?> clazz, @NonNull String methodName, @NonNull Class<?>... parameterTypes) throws NoSuchMethodException {
        Method[] methods = getDeclaredMethods(clazz);
        all:
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;
            Class<?>[] expectedTypes = method.getParameterTypes();
            if (expectedTypes.length != parameterTypes.length) continue;
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] != expectedTypes[i]) continue all;
            }
            return method;
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    /**
     * get a restrict constructor of the given class {@code clazz} with argument types {@code parameterTypes}
     *
     * @param clazz          the class where the expected constructor declares
     * @param parameterTypes argument types of the expected constructor
     * @return the found constructor
     * @throws NoSuchMethodException when no constructor matches the given parameters
     * @see Class#getDeclaredConstructor(Class[])
     */
    @NonNull
    public static Constructor<?> getDeclaredConstructor(@NonNull Class<?> clazz, @NonNull Class<?>... parameterTypes) throws NoSuchMethodException {
        Constructor<?>[] constructors = getDeclaredConstructors(clazz);
        all:
        for (Constructor<?> constructor : constructors) {
            Class<?>[] expectedTypes = constructor.getParameterTypes();
            if (expectedTypes.length != parameterTypes.length) continue;
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] != expectedTypes[i]) continue all;
            }
            return constructor;
        }
        throw new NoSuchMethodException("Cannot find matching constructor");
    }

    /**
     * create an instance of the given class {@code clazz} calling the restricted constructor with arguments {@code args}
     *
     * @param clazz    the class of the instance to new
     * @param initargs arguments to call constructor
     * @return the new instance
     * @see Constructor#newInstance(Object...)
     */
    public static Object newInstance(@NonNull Class<?> clazz, Object... initargs) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?>[] constructors = getDeclaredConstructors(clazz);
        for (Constructor<?> constructor : constructors) {
            Class<?>[] params = constructor.getParameterTypes();
            if (!checkArgsForInvokeMethod(params, initargs)) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(initargs);
        }
        throw new NoSuchMethodException("Cannot find matching constructor");
    }

    /**
     * invoke a restrict method named {@code methodName} of the given class {@code clazz} with this object {@code thiz} and arguments {@code args}
     *
     * @param clazz      the class call the method on (this parameter is required because this method cannot call inherit method)
     * @param thiz       this object, which can be {@code null} if the target method is static
     * @param methodName the method name
     * @param args       arguments to call the method with name {@code methodName}
     * @return the return value of the method
     * @see Method#invoke(Object, Object...)
     */
    public static Object invoke(@NonNull Class<?> clazz, @Nullable Object thiz, @NonNull String methodName, Object... args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method[] methods = getDeclaredMethods(clazz);
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (!checkArgsForInvokeMethod(params, args)) continue;
            method.setAccessible(true);
            return method.invoke(thiz, args);
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    /**
     * Sets the list of exemptions from hidden API access enforcement.
     *
     * @param signaturePrefixes A list of class signature prefixes. Each item in the list is a prefix match on the type
     *                          signature of a blacklisted API. All matching APIs are treated as if they were on
     *                          the whitelist: access permitted, and no logging..
     * @return whether the operation is successful
     */
    public static boolean setHiddenApiExemptions(@NonNull String... signaturePrefixes) {
        try {
            Object runtime = invoke(VMRuntime.class, null, "getRuntime");
            invoke(VMRuntime.class, runtime, "setHiddenApiExemptions", (Object) signaturePrefixes);
            return true;
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "setHiddenApiExemptions", e);
            return false;
        }
    }

    /**
     * Adds the list of exemptions from hidden API access enforcement.
     *
     * @param signaturePrefixes A list of class signature prefixes. Each item in the list is a prefix match on the type
     *                          signature of a blacklisted API. All matching APIs are treated as if they were on
     *                          the whitelist: access permitted, and no logging..
     * @return whether the operation is successful
     */
    public static boolean addHiddenApiExemptions(String... signaturePrefixes) {
        LSPass.signaturePrefixes.addAll(Arrays.asList(signaturePrefixes));
        String[] strings = new String[LSPass.signaturePrefixes.size()];
        LSPass.signaturePrefixes.toArray(strings);
        return setHiddenApiExemptions(strings);
    }

    /**
     * Clear the list of exemptions from hidden API access enforcement.
     * Android runtime will cache access flags, so if a hidden API has been accessed unrestrictedly,
     * running this method will not restore the restriction on it.
     *
     * @return whether the operation is successful
     */
    public static boolean clearHiddenApiExemptions() {
        LSPass.signaturePrefixes.clear();
        return setHiddenApiExemptions();
    }
}
