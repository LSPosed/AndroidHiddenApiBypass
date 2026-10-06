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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import stub.sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
final class MethodHandleBypass {
    private static final int METHOD_ACCESS_PUBLIC = 0x0001;

    private static volatile MethodHandleBypass instance;

    private final Unsafe unsafe;
    private final long artOffset;
    private final long classMethodsOffset;
    private final long artMethodSize;
    private final long artMethodBias;
    private final long executableArtMethodOffset;
    private final long executableAccessFlagsOffset;
    private final long artMethodAccessFlagsOffset;
    private final Class<?> helperClass;
    private final int helperPublicFlags;
    private final int helperConstructorFlags;
    private final int constructorFlagMask;

    static MethodHandleBypass get(Unsafe unsafe, long artOffset, long classMethodsOffset,
                                  long artMethodSize, long artMethodBias,
                                  long executableArtMethodOffset)
            throws ReflectiveOperationException {
        MethodHandleBypass resolver = instance;
        if (resolver != null) return resolver;
        synchronized (MethodHandleBypass.class) {
            resolver = instance;
            if (resolver == null) {
                resolver = new MethodHandleBypass(unsafe, artOffset, classMethodsOffset,
                        artMethodSize, artMethodBias, executableArtMethodOffset);
                instance = resolver;
            }
            return resolver;
        }
    }

    private MethodHandleBypass(Unsafe unsafe, long artOffset, long classMethodsOffset,
                               long artMethodSize, long artMethodBias,
                               long executableArtMethodOffset)
            throws ReflectiveOperationException {
        this.unsafe = unsafe;
        this.artOffset = artOffset;
        this.classMethodsOffset = classMethodsOffset;
        this.artMethodSize = artMethodSize;
        this.artMethodBias = artMethodBias;
        this.executableArtMethodOffset = executableArtMethodOffset;

        Offsets offsets = readOffsets(unsafe);
        executableAccessFlagsOffset = offsets.executableAccessFlagsOffset;

        helperClass = Helper.MethodBridge.class;
        Method helperMethod = helperClass.getDeclaredMethod("m00");
        Constructor<?> helperConstructor = helperClass.getDeclaredConstructor();

        long helperArtMethod = getArtMethod(helperMethod);
        long helperArtConstructor = unsafe.getLong(helperConstructor, executableArtMethodOffset);

        artMethodAccessFlagsOffset = findArtMethodAccessFlagsOffset(helperArtMethod, helperMethod,
                helperArtConstructor, helperConstructor);

        helperPublicFlags = unsafe.getInt(helperArtMethod + artMethodAccessFlagsOffset);
        helperConstructorFlags = unsafe.getInt(helperArtConstructor + artMethodAccessFlagsOffset);
        constructorFlagMask = helperConstructorFlags & ~helperPublicFlags;

        if ((helperPublicFlags & METHOD_ACCESS_PUBLIC) == 0
                || constructorFlagMask == 0) {
            throw new NoSuchMethodException("Helper.MethodBridge");
        }
    }

    @NonNull
    synchronized List<Executable> reflect(@NonNull Class<?> clazz)
            throws ReflectiveOperationException {
        long methods = unsafe.getLong(clazz, classMethodsOffset);
        if (methods == 0) return List.of();

        int numMethods = unsafe.getInt(methods);
        if (numMethods == 0) return List.of();

        long helperMethods = unsafe.getLong(helperClass, classMethodsOffset);
        if (helperMethods == 0 || unsafe.getInt(helperMethods) == 0) {
            throw new NoSuchMethodException("Helper.MethodBridge.methods");
        }

        int helperMethodsLength = unsafe.getInt(helperMethods);
        long helperFirstMethod = helperMethods + artMethodBias;
        long savedHelperMethods = unsafe.allocateMemory(artMethodSize * helperMethodsLength);
        try {
            copyMemory(helperFirstMethod, savedHelperMethods, artMethodSize * helperMethodsLength);
            ArrayList<Executable> result = new ArrayList<>(numMethods);
            for (int start = 0; start < numMethods; start += helperMethodsLength) {
                int batchSize = Math.min(helperMethodsLength, numMethods - start);
                try {
                    for (int slot = 0; slot < batchSize; ++slot) {
                        long originalMethod = methods + artMethodBias + artMethodSize * (start + slot);
                        long helperMethod = helperFirstMethod + artMethodSize * slot;
                        int accessFlags = unsafe.getInt(originalMethod + artMethodAccessFlagsOffset);
                        copyMemory(originalMethod, helperMethod, artMethodSize);
                        unsafe.putInt(helperMethod + artMethodAccessFlagsOffset,
                                (accessFlags & constructorFlagMask) != 0
                                        ? helperConstructorFlags
                                        : helperPublicFlags);
                    }
                    collect(result, helperClass.getDeclaredMethods(), methods,
                            helperFirstMethod, start, batchSize);
                    collect(result, helperClass.getDeclaredConstructors(), methods,
                            helperFirstMethod, start, batchSize);
                } finally {
                    copyMemory(savedHelperMethods, helperFirstMethod,
                            artMethodSize * helperMethodsLength);
                    unsafe.putInt(helperMethods, helperMethodsLength);
                }
            }
            return result;
        } finally {
            copyMemory(savedHelperMethods, helperFirstMethod, artMethodSize * helperMethodsLength);
            unsafe.putInt(helperMethods, helperMethodsLength);
            unsafe.freeMemory(savedHelperMethods);
        }
    }

    private void collect(List<Executable> result, Executable[] reflected, long methods,
                         long helperFirstMethod, int start, int batchSize)
            throws NoSuchMethodException {
        for (Executable executable : reflected) {
            long helperArtMethod = unsafe.getLong(executable, executableArtMethodOffset);
            long slotOffset = helperArtMethod - helperFirstMethod;
            if (slotOffset < 0 || slotOffset % artMethodSize != 0) {
                throw new NoSuchMethodException("Invalid ArtMethod " + helperArtMethod);
            }
            int slot = (int) (slotOffset / artMethodSize);
            if (slot < 0 || slot >= batchSize) {
                continue;
            }
            long originalMethod = methods + artMethodBias + artMethodSize * (start + slot);
            int accessFlags = unsafe.getInt(originalMethod + artMethodAccessFlagsOffset);
            unsafe.putLong(executable, executableArtMethodOffset, originalMethod);
            unsafe.putInt(executable, executableAccessFlagsOffset, accessFlags);
            result.add(executable);
        }
    }

    private long getArtMethod(Method method) throws ReflectiveOperationException {
        method.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflect(method);
        return unsafe.getLong(handle, artOffset);
    }

    private void copyMemory(long from, long to, long count) {
        long offset = 0;
        while (offset + 8 <= count) {
            unsafe.putLong(to + offset, unsafe.getLong(from + offset));
            offset += 8;
        }
        while (offset < count) {
            unsafe.putByte(to + offset, unsafe.getByte(from + offset));
            offset++;
        }
    }

    private long findArtMethodAccessFlagsOffset(long helperMethod, Method javaHelperMethod,
                                                long helperConstructor,
                                                Constructor<?> javaHelperConstructor)
            throws NoSuchMethodException {
        for (long offset = 0; offset < artMethodSize; offset += 4) {
            if ((unsafe.getInt(helperMethod + offset) & 0xffff) == javaHelperMethod.getModifiers()
                    && (unsafe.getInt(helperConstructor + offset) & 0xffff)
                    == javaHelperConstructor.getModifiers()) {
                return offset;
            }
        }
        throw new NoSuchMethodException("ArtMethod.access_flags_");
    }

    private static Offsets readOffsets(Unsafe unsafe) throws ReflectiveOperationException {
        try {
            DexFieldLayout scanner = new DexFieldLayout();
            scanner.scanPath(CoreOjClassLoader.getCoreOjPath());

            DexFieldLayout.Layout executable = scanner.layoutOf(DexFieldLayout.EXECUTABLE);
            return new Offsets(executable.offsetOf("accessFlags"));
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            return readOffsetsClassLoader(unsafe);
        }
    }

    private static Offsets readOffsetsClassLoader(Unsafe unsafe) throws ReflectiveOperationException {
        ClassLoader bootClassloader = new CoreOjClassLoader();
        Class<?> executableClass = bootClassloader.loadClass(Executable.class.getName());
        return new Offsets(unsafe.objectFieldOffset(
                executableClass.getDeclaredField("accessFlags")));
    }

    private static final class Offsets {
        private final long executableAccessFlagsOffset;

        private Offsets(long executableAccessFlagsOffset) {
            this.executableAccessFlagsOffset = executableAccessFlagsOffset;
        }
    }
}
