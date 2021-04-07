package org.lsposed.hiddenapibypass

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Base64
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.VMRuntime
import sun.misc.Unsafe
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
        val unsafe = Unsafe::class.java.getDeclaredMethod("getUnsafe").invoke(null) as Unsafe
        val bufferA = Base64.decode(classA, Base64.DEFAULT)
        val bufferB = Base64.decode(classB, Base64.DEFAULT)
        val cl = InMemoryDexClassLoader(
            arrayOf(ByteBuffer.wrap(bufferA), ByteBuffer.wrap(bufferB)),
            null
        )
        val a = cl.loadClass("A")
        val b = cl.loadClass("B")
        val aDexCache = unsafe.getObject(a, 16)
        val aDexFile = unsafe.getLong(aDexCache, 16)
        val bDexCache = unsafe.getObject(b, 16)
        val bDexFile = unsafe.getLong(bDexCache, 16)
        val last = bDexFile - aDexFile
        val myDexCache = unsafe.getObject(this.javaClass, 16)
        val myDexFile = unsafe.getLong(myDexCache, 16)
        val vmDexCache = unsafe.getObject(VMRuntime::class.java, 16)
        val vmDexFile = unsafe.getLong(vmDexCache, 16)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val off = (last downTo last - 8).firstOrNull {
                unsafe.getByte(myDexFile + it) == 2.toByte()
            } ?: return false
            Log.d("HiddenApiBypass", "offset: $off")
            unsafe.putByte(vmDexFile + off, 2)

            VMRuntime.getRuntime().setHiddenApiExemptions(arrayOf("Landroid/"))
            unsafe.putByte(vmDexFile + off, 0)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            val off = (last downTo last - 8).firstOrNull {
                unsafe.getByte(vmDexFile + it) == 1.toByte()
            } ?: return false
            Log.d("HiddenApiBypass", "offset: $off")
            unsafe.putByte(vmDexFile + off, 0)

            VMRuntime.getRuntime().setHiddenApiExemptions(arrayOf("Landroid/"))
            unsafe.putByte(vmDexFile + off, 1)
        }
        return canAccessHiddenApi().also {
            Log.d("HiddenApiBypass", "bypass: $it")
        }
    }

    private fun canAccessHiddenApi() = runCatching {
        ApplicationInfo::class.java.getMethod("getHiddenApiEnforcementPolicy", *arrayOf())
    }.isSuccess
}
