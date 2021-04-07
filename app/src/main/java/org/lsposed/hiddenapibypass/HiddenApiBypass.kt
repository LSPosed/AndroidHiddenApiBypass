package org.lsposed.hiddenapibypass

import android.content.pm.ApplicationInfo
import android.util.Base64
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.VMRuntime
import java.nio.ByteBuffer


object HiddenApiBypass {
    @JvmStatic
    external fun setHiddenApiExemptions(vararg signaturePrefixes: String): Boolean

    fun nativeBypass(): Boolean {
        if (!setHiddenApiExemptions("Landroid/")) return false

        return canAccessHiddenApi().also {
            Log.d("HiddenApiBypass", "bypass: $it")
        }
    }

    val classA =
        "ZGV4CjAzNQAgLKlP5LhBDcpfC7hMcPQL5/Dg+zmpWle0AQAAcAAAAHhWNBIAAAAAAAAAACwBAAAGAAAAcAAAAAMAAACIAAAAAQAAAJQAAAAAAAAAAAAAAAIAAACgAAAAAQAAALAAAADkAAAA0AAAAOgAAADwAAAA+AAAAP0AAAARAQAAFAEAAAIAAAADAAAABAAAAAQAAAACAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAQAAAAEAAAAAAAAAAQAAAAAAAAAfAQAAAAAAAAEAAQABAAAAGgEAAAQAAABwEAEAAAAOAAY8aW5pdD4ABkEuamF2YQADTEE7ABJMamF2YS9sYW5nL09iamVjdDsAAVYABHRoaXMAAQAHDgAAAAEAAIGABNABAAAACwAAAAAAAAABAAAAAAAAAAEAAAAGAAAAcAAAAAIAAAADAAAAiAAAAAMAAAABAAAAlAAAAAUAAAACAAAAoAAAAAYAAAABAAAAsAAAAAEgAAABAAAA0AAAAAIgAAAGAAAA6AAAAAMgAAABAAAAGgEAAAAgAAABAAAAHwEAAAAQAAABAAAALAEAAA=="
    val classB =
        "ZGV4CjAzNQA/LRMbB/XsZ+rEiwZPplnxH72wtpN64Pe0AQAAcAAAAHhWNBIAAAAAAAAAACwBAAAGAAAAcAAAAAMAAACIAAAAAQAAAJQAAAAAAAAAAAAAAAIAAACgAAAAAQAAALAAAADkAAAA0AAAAOgAAADwAAAA+AAAAP0AAAARAQAAFAEAAAIAAAADAAAABAAAAAQAAAACAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAQAAAAEAAAAAAAAAAQAAAAAAAAAfAQAAAAAAAAEAAQABAAAAGgEAAAQAAABwEAEAAAAOAAY8aW5pdD4ABkIuamF2YQADTEI7ABJMamF2YS9sYW5nL09iamVjdDsAAVYABHRoaXMAAQAHDgAAAAEAAIGABNABAAAACwAAAAAAAAABAAAAAAAAAAEAAAAGAAAAcAAAAAIAAAADAAAAiAAAAAMAAAABAAAAlAAAAAUAAAACAAAAoAAAAAYAAAABAAAAsAAAAAEgAAABAAAA0AAAAAIgAAAGAAAA6AAAAAMgAAABAAAAGgEAAAAgAAABAAAAHwEAAAAQAAABAAAALAEAAA=="

    fun javaBypass(): Boolean {
        val unsafeClass = Class.forName("sun.misc.Unsafe");
        val unsafe = unsafeClass.getDeclaredMethod("getUnsafe").invoke(null)
        val getObject = unsafeClass.getDeclaredMethod(
            "getObject",
            Object::class.java,
            Long::class.javaPrimitiveType
        )
        val getLong = unsafeClass.getDeclaredMethod(
            "getLong",
            Object::class.java,
            Long::class.javaPrimitiveType
        )
        val getByte = unsafeClass.getDeclaredMethod(
            "getByte",
            Long::class.javaPrimitiveType
        )
        val putByte = unsafeClass.getDeclaredMethod(
            "putByte",
            Long::class.javaPrimitiveType,
            Byte::class.javaPrimitiveType
        )
        val bufferA = Base64.decode(classA, Base64.DEFAULT)
        val bufferB = Base64.decode(classB, Base64.DEFAULT)
        val cl = InMemoryDexClassLoader(
            arrayOf(ByteBuffer.wrap(bufferA), ByteBuffer.wrap(bufferB)),
            null
        )
        val a = cl.loadClass("A")
        val b = cl.loadClass("B")
        val aDexCache = getObject.invoke(unsafe, a, 16)
        val aDexFile = getLong(unsafe, aDexCache, 16) as Long
        val bDexCache = getObject.invoke(unsafe, b, 16)
        val bDexFile = getLong(unsafe, bDexCache, 16) as Long
        val last = bDexFile - aDexFile
        val myDexCache = getObject.invoke(unsafe, this.javaClass, 16)
        val myDexFile = getLong.invoke(unsafe, myDexCache, 16) as Long

        val off = (last downTo last - 8).firstOrNull {
            getByte.invoke(
                unsafe,
                myDexFile + it
            ) == 2.toByte()
        } ?: return false
        Log.d("HiddenApiBypass", "offset: $off")
        putByte.invoke(unsafe, myDexFile + off, 0.toByte())

        VMRuntime.getRuntime().setHiddenApiExemptions(arrayOf("Landroid/"))
        putByte.invoke(unsafe, myDexFile + off, 2.toByte())

        return canAccessHiddenApi().also {
            Log.d("HiddenApiBypass", "bypass: $it")
        }
    }

    private fun canAccessHiddenApi() = runCatching {
        ApplicationInfo::class.java.getMethod("getHiddenApiEnforcementPolicy", *arrayOf())
    }.isSuccess
}
