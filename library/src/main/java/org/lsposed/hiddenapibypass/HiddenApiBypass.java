/*
 * Copyright (C) 2021 LSPosed
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

import androidx.annotation.RequiresApi;

import org.lsposed.hiddenapibypass.library.BuildConfig;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dalvik.system.VMRuntime;
import sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
public final class HiddenApiBypass {
    private static final String TAG = "HiddenApiBypass";
    private static final Unsafe unsafe;
    private static final long methodOffset;
    private static final long classOffset;
    private static final long artOffset;
    private static final long infoOffset;
    private static final long methodsOffset;
    private static final long memberOffset;
    private static final long size;
    private static final long bias;
    private static final Set<String> signaturePrefixes = new HashSet<>();

    static {
        try {
            //noinspection JavaReflectionMemberAccess DiscouragedPrivateApi
            unsafe = (Unsafe) Unsafe.class.getDeclaredMethod("getUnsafe").invoke(null);
            assert unsafe != null;
            methodOffset = unsafe.objectFieldOffset(Helper.Executable.class.getDeclaredField("artMethod"));
            classOffset = unsafe.objectFieldOffset(Helper.Executable.class.getDeclaredField("declaringClass"));
            artOffset = unsafe.objectFieldOffset(Helper.MethodHandle.class.getDeclaredField("artFieldOrMethod"));
            infoOffset = unsafe.objectFieldOffset(Helper.MethodHandleImpl.class.getDeclaredField("info"));
            methodsOffset = unsafe.objectFieldOffset(Helper.Class.class.getDeclaredField("methods"));
            memberOffset = unsafe.objectFieldOffset(Helper.HandleInfo.class.getDeclaredField("member"));
            MethodHandle mhA = MethodHandles.lookup().unreflect(Helper.NeverCall.class.getDeclaredMethod("a"));
            MethodHandle mhB = MethodHandles.lookup().unreflect(Helper.NeverCall.class.getDeclaredMethod("b"));
            long aAddr = unsafe.getLong(mhA, artOffset);
            long bAddr = unsafe.getLong(mhB, artOffset);
            long aMethods = unsafe.getLong(Helper.NeverCall.class, methodsOffset);
            size = bAddr - aAddr;
            if (BuildConfig.DEBUG) Log.v(TAG, size + " " +
                    Long.toString(aAddr, 16) + ", " +
                    Long.toString(bAddr, 16) + ", " +
                    Long.toString(aMethods, 16));
            bias = aAddr - aMethods - size;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * create an instance of the given class {@code clazz} calling the restricted constructor with arguments {@code args}
     *
     * @see Constructor#newInstance(Object...)
     *
     * @param clazz the class of the instance to new
     * @param initargs arguments to call constructor
     * @return the new instance
     */
    public static Object newInstance(Class<?> clazz, Object... initargs) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Method stub = Helper.InvokeStub.class.getDeclaredMethod("invoke", Object[].class);
        Constructor<?> ctor = Helper.InvokeStub.class.getDeclaredConstructor(Object[].class);
        ctor.setAccessible(true);
        long methods = unsafe.getLong(clazz, methodsOffset);
        int numMethods = unsafe.getInt(methods);
        checkMethod:
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * size + bias;
            unsafe.putLong(stub, methodOffset, method);
            if ("<init>".equals(stub.getName())) {
                unsafe.putLong(ctor, methodOffset, method);
                unsafe.putObject(ctor, classOffset, clazz);
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != initargs.length) continue;
                for (int j = 0; j < params.length; ++j) {
                    if (!params[j].isInstance(initargs[j])) continue checkMethod;
                }
                return ctor.newInstance(initargs);
            }
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    /**
     * invoke a restrict method named {@code methodName} of the given class {@code clazz} with this object {@code thiz} and arguments {@code args}
     *
     * @see Method#invoke(Object, Object...)
     *
     * @param clazz the class call the method on (this parameter is required because this method cannot call inherit method)
     * @param thiz this object, which can be {@code null} if the target method is static
     * @param methodName the method name
     * @param args arguments to call the method with name {@code methodName}
     * @return the return value of the method
     */
    public static Object invoke(Class<?> clazz, Object thiz, String methodName, Object... args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (thiz != null && !clazz.isInstance(thiz)) {
            throw new IllegalArgumentException("this object is not an instance of the given class");
        }
        Method stub = Helper.InvokeStub.class.getDeclaredMethod("invoke", Object[].class);
        stub.setAccessible(true);
        long methods = unsafe.getLong(clazz, methodsOffset);
        int numMethods = unsafe.getInt(methods);
        Log.d(TAG, clazz + " has " + numMethods + " methods");
        checkMethod:
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * size + bias;
            unsafe.putLong(stub, methodOffset, method);
            Log.v(TAG, "got " + clazz.getTypeName() + "." + stub.getName());
            if (methodName.equals(stub.getName())) {
                Class<?>[] params = stub.getParameterTypes();
                if (params.length != args.length) continue;
                for (int j = 0; j < params.length; ++j) {
                    if (!params[j].isInstance(args[j])) continue checkMethod;
                }
                return stub.invoke(thiz, args);
            }
        }
        throw new NoSuchMethodException("Cannot find matching method");
    }

    /**
     * get declared methods of given class without hidden api restriction
     *
     * @param clazz the class to fetch declared methods
     * @return list of declared methods of {@code clazz}
     */
    public static List<Executable> getDeclaredMethods(Class<?> clazz) {
        ArrayList<Executable> list = new ArrayList<>();
        if (clazz.isPrimitive() || clazz.isArray()) return list;
        MethodHandle mh;
        try {
            mh = MethodHandles.lookup().unreflect(Helper.NeverCall.class.getDeclaredMethod("a"));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return list;
        }
        long methods = unsafe.getLong(clazz, methodsOffset);
        int numMethods = unsafe.getInt(methods);
        if (BuildConfig.DEBUG) Log.d(TAG, clazz + " has " + numMethods + " methods");
        for (int i = 0; i < numMethods; i++) {
            long method = methods + i * size + bias;
            unsafe.putLong(mh, artOffset, method);
            unsafe.putObject(mh, infoOffset, null);
            try {
                MethodHandles.lookup().revealDirect(mh);
            } catch (Throwable ignored) {
            }
            MethodHandleInfo info = (MethodHandleInfo) unsafe.getObject(mh, infoOffset);
            Executable member = (Executable) unsafe.getObject(info, memberOffset);
            if (BuildConfig.DEBUG) Log.v(TAG, "got " + clazz.getTypeName() + "." + member +
                    "(" + Arrays.stream(member.getTypeParameters()).map(Type::getTypeName).collect(Collectors.joining()) + ")");
            list.add(member);
        }
        return list;
    }

    /**
     * Sets the list of exemptions from hidden API access enforcement.
     *
     * @param signaturePrefixes A list of class signature prefixes. Each item in the list is a prefix match on the type
     *                          signature of a blacklisted API. All matching APIs are treated as if they were on
     *                          the whitelist: access permitted, and no logging..
     * @return whether the operation is successful
     */
    public static boolean setHiddenApiExemptions(String... signaturePrefixes) {
        try {
            Object runtime = invoke(VMRuntime.class, null, "getRuntime");
            invoke(VMRuntime.class, runtime, "setHiddenApiExemptions", (Object) signaturePrefixes);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "invoke", e);
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
        HiddenApiBypass.signaturePrefixes.addAll(Arrays.asList(signaturePrefixes));
        String[] strings = new String[HiddenApiBypass.signaturePrefixes.size()];
        HiddenApiBypass.signaturePrefixes.toArray(strings);
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
        HiddenApiBypass.signaturePrefixes.clear();
        return setHiddenApiExemptions();
    }
}
