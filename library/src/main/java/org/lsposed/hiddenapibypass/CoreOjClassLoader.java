package org.lsposed.hiddenapibypass;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import dalvik.system.PathClassLoader;

@RequiresApi(Build.VERSION_CODES.P)
final class CoreOjClassLoader extends PathClassLoader {
    static String getCoreOjPath() {
        String bootClassPath = System.getProperty("java.boot.class.path", "");
        assert bootClassPath != null;
        return bootClassPath.split(":", 2)[0];
    }

    CoreOjClassLoader() {
        super(getCoreOjPath(), null);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (Object.class.getName().equals(name)) {
            return Object.class;
        }
        try {
            return findClass(name);
        } catch (ClassNotFoundException ignored) {
            // no class file in jar before art moved to apex.
        }
        if (Executable.class.getName().equals(name)) {
            return Helper.Executable.class;
        } else if (MethodHandle.class.getName().equals(name)) {
            return Helper.MethodHandle.class;
        } else if (Class.class.getName().equals(name)) {
            return Helper.Class.class;
        } else if (Field.class.getName().equals(name)) {
            return Helper.Field.class;
        }
        return super.loadClass(name);
    }
}
