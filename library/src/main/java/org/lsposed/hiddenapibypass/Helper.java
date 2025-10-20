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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;

@RequiresApi(Build.VERSION_CODES.P)
@SuppressWarnings("unused")
public class Helper {
    static final Set<String> signaturePrefixes = new HashSet<>();

    private static long[] cachedOffsetData = null;

    private static File cacheFile = null;
    private static long artVersion = 0L;

    public static long[] getCachedOffsetData() {
        return cachedOffsetData;
    }

    public static void setCachedOffsetData(long[] data) {
        if (cachedOffsetData != null || data.length != 6) return;
        cachedOffsetData = data;

        if (cacheFile == null) return;
        try (var fos = new FileOutputStream(cacheFile);
             var oos = new ObjectOutputStream(fos)) {
            oos.writeUTF(Build.FINGERPRINT);
            oos.writeLong(artVersion);
            oos.writeObject(cachedOffsetData);
        } catch (IOException ignored) {
        }
    }

    public static void enableOffsetCache(Context context) {
        if (cacheFile != null) return;
        cacheFile = new File(context.getCacheDir(), "HiddenApiBypass");
        artVersion = getArtVersion(context);

        try (var fis = new FileInputStream(cacheFile);
             var ois = new ObjectInputStream(fis)) {
            var fingerprint = ois.readUTF();
            if (!Build.FINGERPRINT.equals(fingerprint)) return;
            var art = ois.readLong();
            if (artVersion != art) return;
            cachedOffsetData = (long[]) ois.readObject();
        } catch (Exception ignored) {
        }
    }

    public static long getArtVersion(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return -1L;
        var pm = context.getPackageManager();
        try {
            var moduleInfo = pm.getModuleInfo("com.android.art", 1);
            var name = moduleInfo.getPackageName();
            if (name == null) return -2L;
            var info = pm.getPackageInfo(name, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            try (var file = new FileReader("/proc/self/mountinfo");
                 var reader = new BufferedReader(file)) {
                var line = reader.lines()
                        .filter(s -> s.contains(" / /apex/com.android.art@"))
                        .findAny();
                if (!line.isPresent()) return -3L;
                var part = line.get().split("@", 2)[1];
                var versionStr = part.split(" ", 2)[0];
                return Long.parseLong(versionStr);
            } catch (Exception e2) {
                return -4L;
            }
        }
    }

    static boolean checkArgsForInvokeMethod(java.lang.Class<?>[] params, Object[] args) {
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

    static public class MethodHandle {
        private final MethodType type = null;
        private MethodType nominalType;
        private MethodHandle cachedSpreadInvoker;
        protected final int handleKind = 0;

        // The ArtMethod* or ArtField* associated with this method handle (used by the runtime).
        protected final long artFieldOrMethod = 0;
    }

    static final public class Class {
        private transient ClassLoader classLoader;
        private transient java.lang.Class<?> componentType;
        private transient Object dexCache;
        private transient Object extData;
        private transient Object[] ifTable;
        private transient String name;
        private transient java.lang.Class<?> superClass;
        private transient Object vtable;
        private transient long iFields;
        private transient long methods;
        private transient long sFields;
        private transient int accessFlags;
        private transient int classFlags;
        private transient int classSize;
        private transient int clinitThreadId;
        private transient int dexClassDefIndex;
        private transient volatile int dexTypeIndex;
        private transient int numReferenceInstanceFields;
        private transient int numReferenceStaticFields;
        private transient int objectSize;
        private transient int objectSizeAllocFastPath;
        private transient int primitiveType;
        private transient int referenceInstanceOffsets;
        private transient int status;
        private transient short copiedMethodsOffset;
        private transient short virtualMethodsOffset;
    }

    static public class AccessibleObject {
        private boolean override;
    }

    static final public class Executable extends AccessibleObject {
        private Class declaringClass;
        private Class declaringClassOfOverriddenMethod;
        private Object[] parameters;
        private long artMethod;
        private int accessFlags;
    }

    @SuppressWarnings("EmptyMethod")
    public static class NeverCall {
        private static void a() {
        }

        private static void b() {
        }

        private static int s;
        private static int t;
        private int i;
        private int j;
    }

    public static class InvokeStub {
        private static Object invoke(Object... args) {
            throw new IllegalStateException("Failed to invoke the method");
        }

        private InvokeStub(Object... args) {
            throw new IllegalStateException("Failed to new a instance");
        }
    }
}
