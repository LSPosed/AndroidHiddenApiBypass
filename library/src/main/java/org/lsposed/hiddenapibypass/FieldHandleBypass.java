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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import stub.sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
final class FieldHandleBypass {
    private static final int FIELD_ACCESS_PUBLIC = 0x0001;
    private static final int FIELD_ACCESS_STATIC = 0x0008;

    private static volatile FieldHandleBypass instance;

    private final Unsafe unsafe;
    private final long artOffset;
    private final long classFieldsOffset;
    private final long artFieldSize;
    private final long artFieldBias;
    private final long artFieldAccessFlagsOffset;
    private final long artFieldDeclaringClassOffset;
    private final long artFieldOffsetOffset;
    private final long fieldAccessFlagsOffset;
    private final long fieldArtFieldIndexOffset;
    private final long fieldDeclaringClassOffset;
    private final long fieldOffsetOffset;
    private final long fieldTypeOffset;
    private final long referenceHolderValueOffset;
    private final Class<?> helperClass;
    private final int helperClassReference;
    private final int helperPublicFlags;

    static FieldHandleBypass get(Unsafe unsafe, long artOffset, long classFieldsOffset,
                                 long artFieldSize, long artFieldBias,
                                 long artFieldAccessFlagsOffset)
            throws ReflectiveOperationException {
        FieldHandleBypass resolver = instance;
        if (resolver != null) return resolver;
        synchronized (FieldHandleBypass.class) {
            resolver = instance;
            if (resolver == null) {
                resolver = new FieldHandleBypass(unsafe, artOffset, classFieldsOffset,
                        artFieldSize, artFieldBias, artFieldAccessFlagsOffset);
                instance = resolver;
            }
            return resolver;
        }
    }

    private FieldHandleBypass(Unsafe unsafe, long artOffset, long classFieldsOffset,
                              long artFieldSize, long artFieldBias,
                              long artFieldAccessFlagsOffset)
            throws ReflectiveOperationException {
        this.unsafe = unsafe;
        this.artOffset = artOffset;
        this.classFieldsOffset = classFieldsOffset;
        this.artFieldSize = artFieldSize;
        this.artFieldBias = artFieldBias;
        this.artFieldAccessFlagsOffset = artFieldAccessFlagsOffset;

        Offsets offsets = readOffsets(unsafe);
        this.fieldAccessFlagsOffset = offsets.fieldAccessFlagsOffset;
        this.fieldArtFieldIndexOffset = offsets.fieldArtFieldIndexOffset;
        this.fieldDeclaringClassOffset = offsets.fieldDeclaringClassOffset;
        this.fieldOffsetOffset = offsets.fieldOffsetOffset;
        this.fieldTypeOffset = offsets.fieldTypeOffset;
        this.referenceHolderValueOffset = unsafe.objectFieldOffset(
                ReferenceHolder.class.getDeclaredField("value"));

        helperClass = Helper.FieldBridge.class;
        Field helperField = helperClass.getDeclaredField("i");
        Field helperNextField = helperClass.getDeclaredField("j");
        Field probeField = Helper.NeverCall.class.getDeclaredField("i");
        Field probeNextField = Helper.NeverCall.class.getDeclaredField("j");

        long helperArtField = getArtField(helperField);
        long helperNextArtField = getArtField(helperNextField);
        long probeArtField = getArtField(probeField);
        long probeNextArtField = getArtField(probeNextField);

        artFieldOffsetOffset = findArtFieldOffsetOffset(helperArtField, helperField,
                helperNextArtField, helperNextField);
        artFieldDeclaringClassOffset = findArtFieldDeclaringClassOffset(helperArtField,
                helperNextArtField, probeArtField, probeNextArtField);

        helperClassReference = unsafe.getInt(helperArtField + artFieldDeclaringClassOffset);
        helperPublicFlags = unsafe.getInt(helperArtField + artFieldAccessFlagsOffset);

        if ((helperPublicFlags & FIELD_ACCESS_PUBLIC) == 0
                || (helperPublicFlags & FIELD_ACCESS_STATIC) != 0) {
            throw new NoSuchFieldException("Helper.FieldBridge.i");
        }
    }

    @NonNull
    synchronized List<Field> reflect(@NonNull Class<?> clazz)
            throws ReflectiveOperationException, IOException {
        long fields = unsafe.getLong(clazz, classFieldsOffset);
        if (fields == 0) return List.of();

        int numFields = unsafe.getInt(fields);
        if (numFields == 0) return List.of();

        long helperFields = unsafe.getLong(helperClass, classFieldsOffset);
        if (helperFields == 0 || unsafe.getInt(helperFields) == 0) {
            throw new NoSuchFieldException("Helper.FieldBridge.fields");
        }
        int helperFieldsLength = unsafe.getInt(helperFields);
        long helperFirstField = helperFields + artFieldBias;
        long savedHelperFields = unsafe.allocateMemory(artFieldSize * helperFieldsLength);
        try {
            copyMemory(helperFirstField, savedHelperFields, artFieldSize * helperFieldsLength);
            Map<String, String> descriptors = FieldTypeCache.get(clazz);
            int targetClassReference = objectReference(clazz);
            ArrayList<Field> result = new ArrayList<>(numFields);
            for (int start = 0; start < numFields; start += helperFieldsLength) {
                int batchSize = Math.min(helperFieldsLength, numFields - start);
                Field[] reflected;
                try {
                    for (int slot = 0; slot < batchSize; ++slot) {
                        long originalField = fields + artFieldBias + artFieldSize * (start + slot);
                        long helperField = helperFirstField + artFieldSize * slot;
                        copyMemory(originalField, helperField, artFieldSize);
                        unsafe.putInt(helperField + artFieldDeclaringClassOffset, helperClassReference);
                        unsafe.putInt(helperField + artFieldAccessFlagsOffset, helperPublicFlags);
                    }
                    unsafe.putInt(helperFields, batchSize);
                    reflected = helperClass.getDeclaredFields();
                } finally {
                    copyMemory(savedHelperFields, helperFirstField, artFieldSize * helperFieldsLength);
                    unsafe.putInt(helperFields, helperFieldsLength);
                }

                if (reflected.length != batchSize) {
                    throw new NoSuchFieldException("Expected " + batchSize
                            + " fields, got " + reflected.length);
                }
                for (Field field : reflected) {
                    int slot = unsafe.getInt(field, fieldArtFieldIndexOffset);
                    if (slot < 0 || slot >= batchSize) {
                        throw new NoSuchFieldException("Invalid artFieldIndex " + slot);
                    }
                    int index = start + slot;
                    long originalField = fields + artFieldBias + artFieldSize * index;
                    if (unsafe.getInt(originalField + artFieldDeclaringClassOffset)
                            != targetClassReference) {
                        continue;
                    }
                    int accessFlags = unsafe.getInt(originalField + artFieldAccessFlagsOffset);
                    int offset = unsafe.getInt(originalField + artFieldOffsetOffset);

                    unsafe.putObject(field, fieldDeclaringClassOffset, clazz);
                    unsafe.putInt(field, fieldArtFieldIndexOffset, index);
                    unsafe.putInt(field, fieldAccessFlagsOffset, accessFlags);
                    unsafe.putInt(field, fieldOffsetOffset, offset);

                    String descriptor = descriptors.get(field.getName());
                    if (descriptor == null) throw new NoSuchFieldException(field.getName());
                    unsafe.putObject(field, fieldTypeOffset,
                            classForDescriptor(descriptor, clazz.getClassLoader()));
                    result.add(field);
                }
            }
            return result;
        } finally {
            copyMemory(savedHelperFields, helperFirstField, artFieldSize * helperFieldsLength);
            unsafe.putInt(helperFields, helperFieldsLength);
            unsafe.freeMemory(savedHelperFields);
        }
    }

    private int objectReference(Object object) {
        ReferenceHolder holder = new ReferenceHolder();
        holder.value = object;
        return unsafe.getInt(holder, referenceHolderValueOffset);
    }

    private long getArtField(Field field) throws ReflectiveOperationException {
        field.setAccessible(true);
        MethodHandle handle = MethodHandles.lookup().unreflectGetter(field);
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

    private long findArtFieldDeclaringClassOffset(long helperField, long helperNextField,
                                                  long probeField, long probeNextField)
            throws NoSuchFieldException {
        for (long offset = 0; offset < artFieldSize; offset += 4) {
            if (offset == artFieldAccessFlagsOffset || offset == artFieldOffsetOffset) continue;
            int helperValue = unsafe.getInt(helperField + offset);
            if (helperValue == unsafe.getInt(helperNextField + offset)
                    && helperValue != unsafe.getInt(probeField + offset)
                    && unsafe.getInt(probeField + offset) == unsafe.getInt(probeNextField + offset)) {
                return offset;
            }
        }
        throw new NoSuchFieldException("ArtField.declaring_class_");
    }

    private long findArtFieldOffsetOffset(long helperField, Field javaHelperField,
                                          long helperNextField, Field javaHelperNextField)
            throws NoSuchFieldException {
        int helperOffset = unsafe.getInt(javaHelperField, fieldOffsetOffset);
        int helperNextOffset = unsafe.getInt(javaHelperNextField, fieldOffsetOffset);
        for (long offset = 0; offset < artFieldSize; offset += 4) {
            if (offset == artFieldAccessFlagsOffset) continue;
            if (unsafe.getInt(helperField + offset) == helperOffset
                    && unsafe.getInt(helperNextField + offset) == helperNextOffset) {
                return offset;
            }
        }
        throw new NoSuchFieldException("ArtField.offset_");
    }

    private static Class<?> classForDescriptor(String descriptor, ClassLoader classLoader)
            throws ClassNotFoundException {
        switch (descriptor.charAt(0)) {
            case 'Z':
                return boolean.class;
            case 'B':
                return byte.class;
            case 'C':
                return char.class;
            case 'S':
                return short.class;
            case 'I':
                return int.class;
            case 'J':
                return long.class;
            case 'F':
                return float.class;
            case 'D':
                return double.class;
            case 'V':
                return void.class;
            case '[':
                return Class.forName(descriptor.replace('/', '.'), false, classLoader);
            case 'L':
                return Class.forName(descriptor.substring(1, descriptor.length() - 1)
                        .replace('/', '.'), false, classLoader);
            default:
                throw new ClassNotFoundException(descriptor);
        }
    }

    private static Offsets readOffsets(Unsafe unsafe) throws ReflectiveOperationException {
        try {
            DexFieldLayout scanner = new DexFieldLayout().want(DexFieldLayout.FIELD);
            scanner.scanPath(CoreOjClassLoader.getCoreOjPath());

            DexFieldLayout.Layout field = scanner.layoutOf(DexFieldLayout.FIELD);
            return new Offsets(
                    field.offsetOf("accessFlags"),
                    field.offsetOf("artFieldIndex"),
                    field.offsetOf("declaringClass"),
                    field.offsetOf("offset"),
                    field.offsetOf("type"));
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            return readOffsetsClassLoader(unsafe);
        }
    }

    private static Offsets readOffsetsClassLoader(Unsafe unsafe) throws ReflectiveOperationException {
        ClassLoader bootClassloader = new CoreOjClassLoader();
        Class<?> fieldClass = bootClassloader.loadClass(Field.class.getName());
        return new Offsets(
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("accessFlags")),
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("artFieldIndex")),
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("declaringClass")),
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("offset")),
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("type")));
    }

    private static String descriptorString(Class<?> clazz) {
        if (clazz.isArray()) return clazz.getName().replace('.', '/');
        return 'L' + clazz.getName().replace('.', '/') + ';';
    }

    private static final class FieldTypeCache {
        private static final Map<Class<?>, Map<String, String>> cache = new WeakHashMap<>();

        private static synchronized Map<String, String> get(Class<?> clazz)
                throws IOException, ClassNotFoundException {
            Map<String, String> cached = cache.get(clazz);
            if (cached != null) return cached;

            String descriptor = descriptorString(clazz);
            for (String path : dexPaths()) {
                if (path.isEmpty()) continue;
                Map<String, String> fields;
                try {
                    fields = DexFieldLayout.findDeclaredFieldTypes(path, descriptor);
                } catch (IOException | RuntimeException ignored) {
                    continue;
                }
                if (fields != null) {
                    cache.put(clazz, fields);
                    return fields;
                }
            }
            throw new ClassNotFoundException(descriptor);
        }

        private static String[] dexPaths() {
            String bootClassPath = System.getProperty("java.boot.class.path", "");
            String classPath = System.getProperty("java.class.path", "");
            if (bootClassPath.isEmpty()) return classPath.split(":");
            if (classPath.isEmpty()) return bootClassPath.split(":");
            return (bootClassPath + ':' + classPath).split(":");
        }
    }

    private static final class Offsets {
        private final long fieldAccessFlagsOffset;
        private final long fieldArtFieldIndexOffset;
        private final long fieldDeclaringClassOffset;
        private final long fieldOffsetOffset;
        private final long fieldTypeOffset;

        private Offsets(long fieldAccessFlagsOffset, long fieldArtFieldIndexOffset,
                        long fieldDeclaringClassOffset, long fieldOffsetOffset,
                        long fieldTypeOffset) {
            this.fieldAccessFlagsOffset = fieldAccessFlagsOffset;
            this.fieldArtFieldIndexOffset = fieldArtFieldIndexOffset;
            this.fieldDeclaringClassOffset = fieldDeclaringClassOffset;
            this.fieldOffsetOffset = fieldOffsetOffset;
            this.fieldTypeOffset = fieldTypeOffset;
        }
    }

    private static final class ReferenceHolder {
        private Object value;
    }
}
