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
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dalvik.system.VMRuntime;
import sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
public final class HiddenApiBypass {
    private static final String TAG = "HiddenApiBypass";
    private static final Unsafe unsafe;
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
        List<Executable> methods = getDeclaredMethods(VMRuntime.class);
        Optional<Executable> getRuntime = methods.stream().filter(it -> it.getName().equals("getRuntime")).findFirst();
        Optional<Executable> setHiddenApiExemptions = methods.stream().filter(it -> it.getName().equals("setHiddenApiExemptions")).findFirst();
        if (getRuntime.isPresent() && setHiddenApiExemptions.isPresent()) {
            getRuntime.get().setAccessible(true);
            try {
                Object runtime = ((Method) getRuntime.get()).invoke(null);
                setHiddenApiExemptions.get().setAccessible(true);
                ((Method) setHiddenApiExemptions.get()).invoke(runtime, (Object) signaturePrefixes);
                return true;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        return false;
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
     *
     * @return whether the operation is successful
     */
    public static boolean clearHiddenApiExemptions() {
        HiddenApiBypass.signaturePrefixes.clear();
        return setHiddenApiExemptions("");
    }
}
