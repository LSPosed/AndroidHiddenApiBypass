package org.lsposed.hiddenapibypass

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Base64
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.VMRuntime
import sun.misc.Unsafe
import java.lang.invoke.MethodHandleInfo
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.nio.ByteBuffer

object HiddenApiBypass {
    @JvmStatic
    external fun setHiddenApiExemptions(vararg signaturePrefixes: String): Boolean
    const val TAG = "HiddenApiBypass"

    fun nativeBypass(vararg signaturePrefixes: String): Boolean {
        return setHiddenApiExemptions(*signaturePrefixes);
    }

    val myVmRuntimeClass =
        "ZGV4CjAzNQBFXgVnpdDckj02DQmM524I4uhKaGBbrg/EAgAAcAAAAHhWNBIAAAAAAAAAADACAAANAAAAcAAAAAUAAACkAAAAAwAAALgAAAAAAAAAAAAAAAUAAADcAAAAAQAAAAQBAACgAQAAJAEAAGIBAABqAQAAbQEAAHwBAACXAQAAqwEAAL0BAADAAQAAxAEAANkBAADlAQAA/QEAABACAAACAAAAAwAAAAQAAAAGAAAACAAAAAEAAAABAAAAAAAAAAYAAAADAAAAAAAAAAcAAAADAAAAXAEAAAAAAQAAAAAAAAACAAoAAAABAAAACQAAAAEAAgAKAAAAAgABAAAAAAAAAAAAAQAAAAIAAAAAAAAABQAAAAAAAAAiAgAAAAAAAAEAAQABAAAAFgIAAAQAAABwEAQAAAAOAAIAAQACAAAAGwIAAAgAAABxAAIAAAAMAG4gAwAQAA4AAQAAAAQABjxpbml0PgABTAANTE15Vk1SdW50aW1lOwAZTGRhbHZpay9zeXN0ZW0vVk1SdW50aW1lOwASTGphdmEvbGFuZy9PYmplY3Q7ABBNeVZNUnVudGltZS5qYXZhAAFWAAJWTAATW0xqYXZhL2xhbmcvU3RyaW5nOwAKZ2V0UnVudGltZQAWc2V0SGlkZGVuQXBpRXhlbXB0aW9ucwARc2lnbmF0dXJlUHJlZml4ZXMABHRoaXMAAwAHDgAFAQwHDngAAAACAACBgASkAgEJvAIMAAAAAAAAAAEAAAAAAAAAAQAAAA0AAABwAAAAAgAAAAUAAACkAAAAAwAAAAMAAAC4AAAABQAAAAUAAADcAAAABgAAAAEAAAAEAQAAASAAAAIAAAAkAQAAARAAAAEAAABcAQAAAiAAAA0AAABiAQAAAyAAAAIAAAAWAgAAACAAAAEAAAAiAgAAABAAAAEAAAAwAgAA"

    fun javaBypass(): Boolean {
        val mh = MethodHandles.lookup().unreflect(
            Helper::class.java.getDeclaredMethod("setHiddenApiExemptions", arrayOf("").javaClass)
        )
        val bufferX = ByteBuffer.wrap(Base64.decode(myVmRuntimeClass, Base64.DEFAULT))
        InMemoryDexClassLoader(bufferX, null)
        val unsafe = Unsafe::class.java.getDeclaredMethod("getUnsafe").invoke(null) as Unsafe
        val artOffset =
            unsafe.objectFieldOffset(Helper.MethodHandle::class.java.getDeclaredField("artFieldOrMethod"))
        val infoOffset =
            unsafe.objectFieldOffset(Helper.MethodHandleImpl::class.java.getDeclaredField("info"))
        val methodOffset =
            unsafe.objectFieldOffset(Helper.DexCache::class.java.getDeclaredField("resolvedMethods"))
        val methodCntOffset =
            unsafe.objectFieldOffset(Helper.DexCache::class.java.getDeclaredField("numResolvedMethods"))
        val memberOffset =
            unsafe.objectFieldOffset(Helper.HandleInfo::class.java.getDeclaredField("member"))

//        Log.d(TAG, "offset: $artOffset, $methodOffset, $methodCntOffset")
        val vmDexCache = unsafe.getObject(VMRuntime::class.java, 16)
        try {
            VMRuntime.getRuntime().setHiddenApiExemptions(arrayOf(""))
        } catch (e: Throwable) {
        }
//        val vmDexFile = unsafe.getLong(vmDexCache, 16)

        val cnt = unsafe.getInt(vmDexCache, methodCntOffset)
        val methods = unsafe.getLong(vmDexCache, methodOffset)
        var dexFileInit: Constructor<*>? = null;
        var loadBinaryClass: Method? = null
        for (i in 0 until cnt) {
            val method = if (unsafe.addressSize() == 4) {
//                Log.d(TAG, unsafe.getInt(methods + i * 8 + 4).toString())
                unsafe.getInt(methods + i * 8).toLong()
            } else {
//                Log.d(TAG, unsafe.getLong(methods + i * 16 + 8).toString())
                unsafe.getLong(methods + i * 16)
            }
            unsafe.putLong(mh, artOffset, method)
            unsafe.putObject(mh, infoOffset, null)
            try {
                MethodHandles.lookup().revealDirect(mh)
            } catch (e: Throwable) {
            }
            val info = unsafe.getObject(mh, infoOffset) as MethodHandleInfo
//            Log.d(TAG, "${info.declaringClass}.${info.name}")
            if (info.declaringClass.name == "dalvik.system.DexFile") {
                when (info.name) {
                    "<init>" -> {
                        val member = unsafe.getObject(info, memberOffset) as Constructor<*>
                        if (member.parameterTypes[0].isArray) {
                            member.isAccessible = true
                            dexFileInit = member
                        }
                    }
                    "loadClassBinaryName" -> {
                        val member = unsafe.getObject(info, memberOffset) as Method
                        member.isAccessible = true
                        loadBinaryClass = member
                    }
                }
            }

        }

        return if (dexFileInit != null && loadBinaryClass != null) {
            try {
                val dex = dexFileInit.newInstance(arrayOf(bufferX), null, null)
                val myVm = loadBinaryClass.invoke(dex, "MyVMRuntime", null, null)!! as Class<*>
                myVm.getDeclaredMethod("setHiddenApiExemptions", arrayOf("").javaClass)
                    .invoke(null, arrayOf("Landroid/"))
                true
            } catch (e: Throwable) {
                false
            }
        } else false
    }
}
