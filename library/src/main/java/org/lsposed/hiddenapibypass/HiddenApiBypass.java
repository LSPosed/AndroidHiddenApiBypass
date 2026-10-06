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

import java.io.IOException;
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
import java.util.List;
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
    private static final long artFieldAccessFlagsOffset;

    static {
        try {
            //noinspection JavaReflectionMemberAccess DiscouragedPrivateApi
            unsafe = (Unsafe) Unsafe.class.getDeclaredMethod("getUnsafe").invoke(null);
            var data = Helper.getCachedOffsetData();
            if (data == null) {
                data = readOffsetDataIO();
                Helper.setCachedOffsetData(data);
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "Using cached offset data");
            }
            methodOffset = data[0];
            classOffset = data[1];
            artOffset = data[2];
            methodsOffset = data[3];
            iFieldOffset = data[4];
            sFieldOffset = data[5];
            var dataRT = readOffsetDataRT();
            artMethodSize = dataRT[0];
            artMethodBias = dataRT[1];
            artFieldSize = dataRT[2];
            artFieldBias = dataRT[3];
            artFieldAccessFlagsOffset = dataRT[4];
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Initialize error", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private static long[] readOffsetDataIO() throws ReflectiveOperationException {
        try {
            return readOffsetDataDex();
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read offset data from dex", e);
        }
        return readOffsetDataClassLoader();
    }

    private static long[] readOffsetDataDex() throws IOException, ReflectiveOperationException {
        var scanner = new DexFieldLayout();
        scanner.scanPath(CoreOjClassLoader.getCoreOjPath());
        var executable = scanner.layoutOf(DexFieldLayout.EXECUTABLE);
        var methodHandle = scanner.layoutOf(DexFieldLayout.METHOD_HANDLE);
        var classClass = scanner.layoutOf(DexFieldLayout.CLASS);

        var data = new long[6];
        data[0] = executable.offsetOf("artMethod");
        data[1] = executable.offsetOf("declaringClass");
        data[2] = methodHandle.offsetOf("artFieldOrMethod");
        data[3] = classClass.offsetOf("methods");
        if (classClass.hasField("fields")) {
            data[4] = classClass.offsetOf("fields");
            data[5] = data[4];
        } else {
            data[4] = classClass.offsetOf("iFields");
            data[5] = classClass.offsetOf("sFields");
        }
        return data;
    }

    private static long[] readOffsetDataClassLoader() throws ReflectiveOperationException {
        ClassLoader bootClassloader = new CoreOjClassLoader();
        Class<?> executableClass = bootClassloader.loadClass(Executable.class.getName());
        Class<?> methodHandleClass = bootClassloader.loadClass(MethodHandle.class.getName());
        Class<?> classClass = bootClassloader.loadClass(Class.class.getName());

        var data = new long[6];
        data[0] = unsafe.objectFieldOffset(executableClass.getDeclaredField("artMethod"));
        data[1] = unsafe.objectFieldOffset(executableClass.getDeclaredField("declaringClass"));
        data[2] = unsafe.objectFieldOffset(methodHandleClass.getDeclaredField("artFieldOrMethod"));
        data[3] = unsafe.objectFieldOffset(classClass.getDeclaredField("methods"));
        try {
            data[4] = unsafe.objectFieldOffset(classClass.getDeclaredField("fields"));
            data[5] = data[4];
        } catch (NoSuchFieldException e) {
            data[4] = unsafe.objectFieldOffset(classClass.getDeclaredField("iFields"));
            data[5] = unsafe.objectFieldOffset(classClass.getDeclaredField("sFields"));
        }
        return data;
    }

    private static long[] readOffsetDataRT() throws ReflectiveOperationException {
        Method mA = Helper.NeverCall.class.getDeclaredMethod("a");
        Method mB = Helper.NeverCall.class.getDeclaredMethod("b");
        mA.setAccessible(true);
        mB.setAccessible(true);
        MethodHandle mhA = MethodHandles.lookup().unreflect(mA);
        MethodHandle mhB = MethodHandles.lookup().unreflect(mB);
        long aAddr = unsafe.getLong(mhA, artOffset);
        long bAddr = unsafe.getLong(mhB, artOffset);
        long aMethods = unsafe.getLong(Helper.NeverCall.class, methodsOffset);
        var artMethodSize = bAddr - aAddr;
        if (BuildConfig.DEBUG) Log.v(TAG, artMethodSize + " " +
                Long.toString(aAddr, 16) + ", " +
                Long.toString(bAddr, 16) + ", " +
                Long.toString(aMethods, 16));
        var artMethodBias = aAddr - aMethods - artMethodSize;

        Field fI = Helper.NeverCall.class.getDeclaredField("i");
        Field fJ = Helper.NeverCall.class.getDeclaredField("j");
        Field fS = Helper.NeverCall.class.getDeclaredField("s");
        fI.setAccessible(true);
        fJ.setAccessible(true);
        fS.setAccessible(true);
        MethodHandle mhI = MethodHandles.lookup().unreflectGetter(fI);
        MethodHandle mhJ = MethodHandles.lookup().unreflectGetter(fJ);
        MethodHandle mhS = MethodHandles.lookup().unreflectGetter(fS);
        long iAddr = unsafe.getLong(mhI, artOffset);
        long jAddr = unsafe.getLong(mhJ, artOffset);
        long sAddr = unsafe.getLong(mhS, artOffset);
        long iFields = unsafe.getLong(Helper.NeverCall.class, iFieldOffset);
        var artFieldSize = jAddr - iAddr;
        if (BuildConfig.DEBUG) Log.v(TAG, artFieldSize + " " +
                Long.toString(iAddr, 16) + ", " +
                Long.toString(jAddr, 16) + ", " +
                Long.toString(iFields, 16));
        var artFieldBias = iAddr - iFields;
        var artFieldAccessFlagsOffset = -1L;
        for (long offset = 0; offset < artFieldSize; offset += 4) {
            if ((unsafe.getInt(iAddr + offset) & 0xffff) == fI.getModifiers()
                    && (unsafe.getInt(sAddr + offset) & 0xffff) == fS.getModifiers()) {
                artFieldAccessFlagsOffset = offset;
                break;
            }
        }
        if (artFieldAccessFlagsOffset < 0) throw new NoSuchFieldException("ArtField.access_flags_");

        long[] data = new long[5];
        data[0] = artMethodSize;
        data[1] = artMethodBias;
        data[2] = artFieldSize;
        data[3] = artFieldBias;
        data[4] = artFieldAccessFlagsOffset;
        return data;
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
                Class<?>[] params = ctor.getParameterTypes();
                if (Helper.checkArgsForInvokeMethod(params, initargs)) {
                    unsafe.putObject(ctor, classOffset, clazz);
                    return ctor.newInstance(initargs);
                }
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
        List<Field> fields = getFieldsFromArt(clazz, false);
        if (fields != null) return fields;
        return List.of();
    }

    @Nullable
    private static List<Field> getFieldsFromArt(@NonNull Class<?> clazz, boolean wantStatic) {
        if (iFieldOffset != sFieldOffset) return null;
        FieldHandleBypass resolver;
        try {
            resolver = FieldHandleBypass.get(unsafe, artOffset, iFieldOffset, artFieldSize,
                    artFieldBias, artFieldAccessFlagsOffset);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to initialize field handle resolver", e);
            return null;
        }
        List<Field> fields;
        try {
            fields = resolver.reflect(clazz);
        } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to materialize fields", e);
            return null;
        }
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + fields.size() + " fields");
        List<Field> list = new ArrayList<>(fields.size());
        for (Field member : fields) {
            if (member.getDeclaringClass() != clazz) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Materialized field from wrong class: " + member);
                return null;
            }
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "got " + member.getType() + " " + clazz.getTypeName() + "." + member.getName());
            }
            if (Modifier.isStatic(member.getModifiers()) == wantStatic) list.add(member);
        }
        return list;
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
        List<Field> fields = getFieldsFromArt(clazz, true);
        if (fields != null) return fields;
        return List.of();
    }

    /**
     * Sets the list of exemptions from hidden API access enforcement.
     *
     * @param signaturePrefixes A list of class signature prefixes. Each item in the list is a prefix match on the type
     *                          signature of a blacklisted API. All matching APIs are treated as if they were on
     *                          the whitelist: access permitted, and no logging.
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
     *                          the whitelist: access permitted, and no logging.
     * @return whether the operation is successful
     *
     * @deprecated {@link VMRuntime#setHiddenApiExemptions(String[])} cannot be called more than once.
     * In a future Android release that will either be no-op or throw an exception.
     */
    @Deprecated
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
     *
     * @deprecated {@link VMRuntime#setHiddenApiExemptions(String[])} cannot be called more than once.
     * In a future Android release that will either be no-op or throw an exception.
     */
    @Deprecated
    public static boolean clearHiddenApiExemptions() {
        Helper.signaturePrefixes.clear();
        return setHiddenApiExemptions();
    }
}
