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

import dalvik.system.PathClassLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import stub.sun.misc.Unsafe;

@RequiresApi(Build.VERSION_CODES.P)
final class FieldHandleBypass {
    private static final Map<String, FieldHandleBypass> instances = new HashMap<>();

    private final Unsafe unsafe;
    private final long classFieldsOffset;
    private final long fieldDeclaringClassOffset;
    private final long fieldTypeOffset;

    static FieldHandleBypass get(Unsafe unsafe, long artOffset, long classFieldsOffset,
                                 long helperFieldsOffset,
                                 long artFieldSize, long artFieldBias,
                                 long artFieldAccessFlagsOffset)
            throws ReflectiveOperationException {
        String key = classFieldsOffset + ":" + helperFieldsOffset;
        synchronized (FieldHandleBypass.class) {
            FieldHandleBypass resolver = instances.get(key);
            if (resolver == null) {
                resolver = new FieldHandleBypass(unsafe, classFieldsOffset);
                instances.put(key, resolver);
            }
            return resolver;
        }
    }

    private FieldHandleBypass(Unsafe unsafe, long classFieldsOffset)
            throws ReflectiveOperationException {
        this.unsafe = unsafe;
        this.classFieldsOffset = classFieldsOffset;

        Offsets offsets = readOffsetsClassLoader(unsafe);
        fieldDeclaringClassOffset = offsets.fieldDeclaringClassOffset;
        fieldTypeOffset = offsets.fieldTypeOffset;
    }

    @NonNull
    List<Field> reflect(@NonNull Class<?> clazz)
            throws ReflectiveOperationException, IOException {
        long fields = unsafe.getLong(clazz, classFieldsOffset);
        if (fields == 0 || unsafe.getInt(fields) == 0) return List.of();

        Class<?> clonedClass = CloneClassCache.get(clazz);
        if (clonedClass == clazz) throw new ClassNotFoundException(clazz.getName());

        Field[] fieldsFromClone = clonedClass.getDeclaredFields();
        ArrayList<Field> result = new ArrayList<>(fieldsFromClone.length);
        for (Field field : fieldsFromClone) {
            unsafe.putObject(field, fieldDeclaringClassOffset, clazz);
            if (unsafe.getObject(field, fieldTypeOffset) == clonedClass) {
                unsafe.putObject(field, fieldTypeOffset, clazz);
            }
            result.add(field);
        }
        return result;
    }

    private static final class CloneClassCache {
        private static final Map<Class<?>, Class<?>> classes = new WeakHashMap<>();

        private static synchronized Class<?> get(Class<?> clazz) throws ClassNotFoundException {
            Class<?> cached = classes.get(clazz);
            if (cached != null) return cached;

            ArrayList<String> paths = DexPathFinder.paths(clazz);
            if (paths.isEmpty()) throw new ClassNotFoundException(clazz.getName());

            ClassLoader parent = clazz.getClassLoader();
            if (parent == null) parent = FieldHandleBypass.class.getClassLoader();

            var loader = new CloneClassLoader(joinDexPaths(paths), parent, clazz.getName());
            Class<?> cloned = Class.forName(clazz.getName(), false, loader);
            classes.put(clazz, cloned);
            return cloned;
        }

        private static String joinDexPaths(ArrayList<String> paths) {
            StringBuilder builder = new StringBuilder();
            for (String path : paths) {
                if (builder.length() != 0) builder.append(':');
                builder.append(path);
            }
            return builder.toString();
        }
    }

    private static final class CloneClassLoader extends PathClassLoader {
        private final String targetClassName;

        private CloneClassLoader(String dexPath, ClassLoader parent, String targetClassName) {
            super(dexPath, parent);
            this.targetClassName = targetClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> clazz = findLoadedClass(name);
                if (clazz == null && targetClassName.equals(name)) {
                    try {
                        clazz = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (clazz == null) clazz = super.loadClass(name, false);
                if (resolve) resolveClass(clazz);
                return clazz;
            }
        }
    }

    private static final class DexPathFinder {
        private static ArrayList<String> paths(Class<?> clazz) {
            ArrayList<String> paths = new ArrayList<>();

            ClassLoader classLoader = clazz.getClassLoader();
            while (classLoader != null) {
                addClassLoaderPaths(paths, classLoader.toString());
                classLoader = classLoader.getParent();
            }

            addDexPaths(paths, System.getProperty("java.class.path", ""));
            addDexPaths(paths, System.getProperty("java.boot.class.path", ""));
            addDexPaths(paths, System.getenv("BOOTCLASSPATH"));
            addDexPaths(paths, System.getenv("DEX2OATBOOTCLASSPATH"));
            addMappedDexPaths(paths);
            return paths;
        }

        private static void addDexPaths(ArrayList<String> paths, String value) {
            if (value == null || value.isEmpty()) return;
            for (String path : value.split(":")) {
                addPath(paths, path);
            }
        }

        private static void addClassLoaderPaths(ArrayList<String> paths, String value) {
            int start = 0;
            while (true) {
                start = value.indexOf('"', start);
                if (start < 0) return;
                start++;
                int end = value.indexOf('"', start);
                if (end < 0) return;
                addPath(paths, value.substring(start, end));
                start = end + 1;
            }
        }

        private static void addMappedDexPaths(ArrayList<String> paths) {
            try (var reader = Files.newBufferedReader(Paths.get("/proc/self/maps"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int start = line.indexOf('/');
                    if (start < 0) continue;
                    int end = line.indexOf(" (deleted)", start);
                    addPath(paths, end < 0 ? line.substring(start) : line.substring(start, end));
                }
            } catch (IOException | SecurityException ignored) {
            }
        }

        private static void addPath(ArrayList<String> paths, String path) {
            if (isDexPath(path) && !paths.contains(path)) paths.add(path);
        }

        private static boolean isDexPath(String path) {
            return path.endsWith(".apk")
                    || path.endsWith(".jar")
                    || path.endsWith(".dex");
        }
    }

    private static Offsets readOffsetsClassLoader(Unsafe unsafe) throws ReflectiveOperationException {
        ClassLoader bootClassloader = new CoreOjClassLoader();
        Class<?> fieldClass = bootClassloader.loadClass(Field.class.getName());
        return new Offsets(
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("declaringClass")),
                unsafe.objectFieldOffset(fieldClass.getDeclaredField("type")));
    }

    private static final class Offsets {
        private final long fieldDeclaringClassOffset;
        private final long fieldTypeOffset;

        private Offsets(long fieldDeclaringClassOffset, long fieldTypeOffset) {
            this.fieldDeclaringClassOffset = fieldDeclaringClassOffset;
            this.fieldTypeOffset = fieldTypeOffset;
        }
    }
}
