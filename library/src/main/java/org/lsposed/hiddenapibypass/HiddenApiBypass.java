/*
 * Copyright (C) 2021-2025 LSPosed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lsposed.hiddenapibypass;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.lsposed.hiddenapibypass.library.BuildConfig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import stub.dalvik.system.VMRuntime;
import stub.sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
public final class HiddenApiBypass {
    private static final String TAG = "HiddenApiBypass";
    private static final Unsafe unsafe;
    private static final long methodOffset;
    private static final long classOffset;
    private static final long artOffset;
    private static final long methodsOffset;
    private static final long iFieldOffset;
    private static final long sFieldOffset;
    private static final long artMethodSize;
    private static final long artMethodBias;
    private static final long artFieldSize;
    private static final long artFieldBias;
    private static final ClassLoader bootClassloader;
    private static final long fieldDeclaringClassOffset;
    private static final long fieldTypeOffset;
    private static final boolean instanceFieldHandleSupported;
    private static final boolean staticFieldHandleSupported;
    private static final Map<String, ClassLoader> fieldClassLoaders = new HashMap<>();

    static {
        try {
            //noinspection JavaReflectionMemberAccess DiscouragedPrivateApi
            unsafe = (Unsafe) Unsafe.class.getDeclaredMethod("getUnsafe").invoke(null);
            assert unsafe != null;
            bootClassloader = new CoreOjClassLoader();
            Class<?> executableClass = bootClassloader.loadClass(Executable.class.getName());
            Class<?> methodHandleClass = bootClassloader.loadClass(MethodHandle.class.getName());
            Class<?> classClass = bootClassloader.loadClass(Class.class.getName());
            Class<?> fieldClass = bootClassloader.loadClass(Field.class.getName());
            methodOffset = unsafe.objectFieldOffset(executableClass.getDeclaredField("artMethod"));
            classOffset = unsafe.objectFieldOffset(executableClass.getDeclaredField("declaringClass"));
            artOffset = unsafe.objectFieldOffset(methodHandleClass.getDeclaredField("artFieldOrMethod"));
            fieldDeclaringClassOffset = unsafe.objectFieldOffset(fieldClass.getDeclaredField("declaringClass"));
            fieldTypeOffset = unsafe.objectFieldOffset(fieldClass.getDeclaredField("type"));
            long iField;
            long sField;
            try {
                iField = unsafe.objectFieldOffset(classClass.getDeclaredField("fields"));
                sField = iField;
            } catch (NoSuchFieldException e) {
                iField = unsafe.objectFieldOffset(classClass.getDeclaredField("iFields"));
                sField = unsafe.objectFieldOffset(classClass.getDeclaredField("sFields"));
            }
            iFieldOffset = iField;
            sFieldOffset = sField;
            methodsOffset = unsafe.objectFieldOffset(classClass.getDeclaredField("methods"));
            Method mA = Helper.NeverCall.class.getDeclaredMethod("a");
            Method mB = Helper.NeverCall.class.getDeclaredMethod("b");
            mA.setAccessible(true);
            mB.setAccessible(true);
            MethodHandle mhA = MethodHandles.lookup().unreflect(mA);
            MethodHandle mhB = MethodHandles.lookup().unreflect(mB);
            long aAddr = unsafe.getLong(mhA, artOffset);
            long bAddr = unsafe.getLong(mhB, artOffset);
            long aMethods = unsafe.getLong(Helper.NeverCall.class, methodsOffset);
            artMethodSize = bAddr - aAddr;
            if (BuildConfig.DEBUG) Log.v(TAG, artMethodSize + " " +
                    Long.toString(aAddr, 16) + ", " +
                    Long.toString(bAddr, 16) + ", " +
                    Long.toString(aMethods, 16));
            artMethodBias = aAddr - aMethods - artMethodSize;
            Field fI = Helper.NeverCall.class.getDeclaredField("i");
            Field fJ = Helper.NeverCall.class.getDeclaredField("j");
            fI.setAccessible(true);
            fJ.setAccessible(true);
            MethodHandle mhI = MethodHandles.lookup().unreflectGetter(fI);
            MethodHandle mhJ = MethodHandles.lookup().unreflectGetter(fJ);
            long iAddr = unsafe.getLong(mhI, artOffset);
            long jAddr = unsafe.getLong(mhJ, artOffset);
            long iFields = unsafe.getLong(Helper.NeverCall.class, iFieldOffset);
            artFieldSize = jAddr - iAddr;
            if (BuildConfig.DEBUG) Log.v(TAG, artFieldSize + " " +
                    Long.toString(iAddr, 16) + ", " +
                    Long.toString(jAddr, 16) + ", " +
                    Long.toString(iFields, 16));
            artFieldBias = iAddr - iFields;
            instanceFieldHandleSupported = isFieldHandleSupported("i", "j");
            staticFieldHandleSupported = isFieldHandleSupported("s", "t");
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Initialize error", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private static ClassLoader getFieldClassLoader(String path) {
        synchronized (fieldClassLoaders) {
            ClassLoader classLoader = fieldClassLoaders.get(path);
            if (classLoader == null) {
                classLoader = new CoreOjClassLoader(path, HiddenApiBypass.class.getClassLoader());
                fieldClassLoaders.put(path, classLoader);
            }
            return classLoader;
        }
    }

    private static boolean isFieldHandleSupported(String sourceName, String targetName) {
        try {
            Field source = Helper.NeverCall.class.getDeclaredField(sourceName);
            Field target = Helper.NeverCall.class.getDeclaredField(targetName);
            source.setAccessible(true);
            target.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflectGetter(source);
            MethodHandle targetHandle = MethodHandles.lookup().unreflectGetter(target);
            long sourceArtField = unsafe.getLong(handle, artOffset);
            long targetArtField = unsafe.getLong(targetHandle, artOffset);
            try {
                unsafe.putLong(handle, artOffset, targetArtField);
                Field reflected = MethodHandles.reflectAs(Field.class, handle);
                return reflected.getDeclaringClass() == Helper.NeverCall.class
                        && targetName.equals(reflected.getName());
            } finally {
                unsafe.putLong(handle, artOffset, sourceArtField);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "MethodHandle field check failed", e);
            return false;
        }
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
        Method stub = Helper.InvokeStub.class.getDeclaredMethod("invoke", Object[].class);
        Constructor<?> ctor = Helper.InvokeStub.class.getDeclaredConstructor(Object[].class);
        ctor.setAccessible(true);
        long methods = unsafe.getLong(clazz, methodsOffset);
        if (methods == 0) throw new NoSuchMethodException("Cannot find matching constructor");
        int numMethods = unsafe.getInt(methods);
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + numMethods + " methods");
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * artMethodSize + artMethodBias;
            unsafe.putLong(stub, methodOffset, method);
            if (BuildConfig.DEBUG) Log.v(TAG, "got " + clazz.getTypeName() + "." + stub.getName() +
                    "(" + Arrays.stream(stub.getParameterTypes()).map(Type::getTypeName).collect(Collectors.joining()) + ")");
            if ("<init>".equals(stub.getName())) {
                unsafe.putLong(ctor, methodOffset, method);
                unsafe.putObject(ctor, classOffset, clazz);
                Class<?>[] params = ctor.getParameterTypes();
                if (Helper.checkArgsForInvokeMethod(params, initargs))
                    return ctor.newInstance(initargs);
            }
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
        if (thiz != null && !clazz.isInstance(thiz)) {
            throw new IllegalArgumentException("this object is not an instance of the given class");
        }
        Method stub = Helper.InvokeStub.class.getDeclaredMethod("invoke", Object[].class);
        stub.setAccessible(true);
        long methods = unsafe.getLong(clazz, methodsOffset);
        if (methods == 0) throw new NoSuchMethodException("Cannot find matching method");
        int numMethods = unsafe.getInt(methods);
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + numMethods + " methods");
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * artMethodSize + artMethodBias;
            unsafe.putLong(stub, methodOffset, method);
            if (BuildConfig.DEBUG) Log.v(TAG, "got " + clazz.getTypeName() + "." + stub.getName() +
                    "(" + Arrays.stream(stub.getParameterTypes()).map(Type::getTypeName).collect(Collectors.joining()) + ")");
            if (methodName.equals(stub.getName())) {
                Class<?>[] params = stub.getParameterTypes();
                if (Helper.checkArgsForInvokeMethod(params, args))
                    return stub.invoke(thiz, args);
            }
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    /**
     * get declared methods of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods (including constructors with name `&lt;init&gt;`)
     * @return list of declared methods of {@code clazz}
     */
    @NonNull
    public static List<Executable> getDeclaredMethods(@NonNull Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) return List.of();
        MethodHandle mh;
        try {
            Method mA = Helper.NeverCall.class.getDeclaredMethod("a");
            mA.setAccessible(true);
            mh = MethodHandles.lookup().unreflect(mA);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return List.of();
        }
        long methods = unsafe.getLong(clazz, methodsOffset);
        if (methods == 0) return List.of();
        int numMethods = unsafe.getInt(methods);
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + numMethods + " methods");
        List<Executable> list = new ArrayList<>(numMethods);
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * artMethodSize + artMethodBias;
            unsafe.putLong(mh, artOffset, method);
            Executable member = MethodHandles.reflectAs(Executable.class, mh);
            if (BuildConfig.DEBUG)
                Log.v(TAG, "got " + clazz.getTypeName() + "." + member.getName() +
                        "(" + Arrays.stream(member.getParameterTypes()).map(Type::getTypeName).collect(Collectors.joining()) + ")");
            list.add(member);
        }
        return list;
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
        List<Executable> methods = getDeclaredMethods(clazz);
        allMethods:
        for (Executable method : methods) {
            if (!method.getName().equals(methodName)) continue;
            if (!(method instanceof Method)) continue;
            Class<?>[] expectedTypes = method.getParameterTypes();
            if (expectedTypes.length != parameterTypes.length) continue;
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] != expectedTypes[i]) continue allMethods;
            }
            return (Method) method;
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
        List<Executable> methods = getDeclaredMethods(clazz);
        allMethods:
        for (Executable method : methods) {
            if (!(method instanceof Constructor)) continue;
            Class<?>[] expectedTypes = method.getParameterTypes();
            if (expectedTypes.length != parameterTypes.length) continue;
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] != expectedTypes[i]) continue allMethods;
            }
            return (Constructor<?>) method;
        }
        throw new NoSuchMethodException("Cannot find matching constructor");
    }


    /**
     * get declared non-static fields of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods
     * @return list of declared non-static fields of {@code clazz}
     */
    @NonNull
    public static List<Field> getInstanceFields(@NonNull Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) return List.of();
        if (!instanceFieldHandleSupported) return getFieldsFromClassLoader(clazz, false);
        return getFieldsFromMethodHandle(clazz, false);
    }

    @NonNull
    private static List<Field> getFieldsFromMethodHandle(@NonNull Class<?> clazz, boolean wantStatic) {
        MethodHandle mh;
        try {
            Field stub = Helper.NeverCall.class.getDeclaredField(wantStatic ? "s" : "i");
            stub.setAccessible(true);
            mh = MethodHandles.lookup().unreflectGetter(stub);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            return List.of();
        }
        long fields = unsafe.getLong(clazz, wantStatic ? sFieldOffset : iFieldOffset);
        if (fields == 0) return List.of();
        int numFields = unsafe.getInt(fields);
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + numFields + " fields");
        List<Field> list = new ArrayList<>(numFields);
        for (int i = 0; i < numFields; i++) {
            long field = fields + i * artFieldSize + artFieldBias;
            unsafe.putLong(mh, artOffset, field);
            Field member = MethodHandles.reflectAs(Field.class, mh);
            if (BuildConfig.DEBUG)
                Log.v(TAG, "got " + member.getType() + " " + clazz.getTypeName() + "." + member.getName());
            if (Modifier.isStatic(member.getModifiers()) == wantStatic)
                list.add(member);
        }
        return list;
    }

    @NonNull
    private static List<Field> getFieldsFromClassLoader(@NonNull Class<?> clazz, boolean wantStatic) {
        try {
            Class<?> clonedClass = loadCloneClass(clazz);
            Field[] fields = clonedClass.getDeclaredFields();
            List<Field> list = new ArrayList<>(fields.length);
            for (Field field : fields) {
                unsafe.putObject(field, fieldDeclaringClassOffset, clazz);
                if (unsafe.getObject(field, fieldTypeOffset) == clonedClass) {
                    unsafe.putObject(field, fieldTypeOffset, clazz);
                }
                if (Modifier.isStatic(field.getModifiers()) == wantStatic) {
                    list.add(field);
                }
            }
            return list;
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to materialize fields with classloader", e);
            return List.of();
        }
    }

    private static Class<?> loadCloneClass(Class<?> clazz) throws ClassNotFoundException {
        String className = clazz.getName();
        for (String path : getFieldClassLoaderPaths()) {
            try {
                Class<?> clonedClass = getFieldClassLoader(path).loadClass(className);
                if (clonedClass != clazz) return clonedClass;
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(className);
    }

    private static List<String> getFieldClassLoaderPaths() {
        String bootClassPath = CoreOjClassLoader.getBootClassPath();
        ArrayList<String> paths = new ArrayList<>();
        addFieldClassLoaderPath(paths, bootClassPath, "/system/framework/framework.jar");
        for (String path : bootClassPath.split(":")) {
            addFieldClassLoaderPath(paths, bootClassPath, path);
        }
        return paths;
    }

    private static void addFieldClassLoaderPath(ArrayList<String> paths, String bootClassPath, String path) {
        if (path.isEmpty() || paths.contains(path)) return;
        if (bootClassPath.equals(path)
                || bootClassPath.startsWith(path + ":")
                || bootClassPath.endsWith(":" + path)
                || bootClassPath.contains(":" + path + ":")) {
            paths.add(path);
        }
    }

    /**
     * get declared static fields of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods
     * @return list of declared static fields of {@code clazz}
     */
    @NonNull
    public static List<Field> getStaticFields(@NonNull Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray()) return List.of();
        if (!staticFieldHandleSupported) return getFieldsFromClassLoader(clazz, true);
        return getFieldsFromMethodHandle(clazz, true);
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
        Helper.signaturePrefixes.addAll(Arrays.asList(signaturePrefixes));
        String[] strings = new String[Helper.signaturePrefixes.size()];
        Helper.signaturePrefixes.toArray(strings);
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
        Helper.signaturePrefixes.clear();
        return setHiddenApiExemptions();
    }
}
