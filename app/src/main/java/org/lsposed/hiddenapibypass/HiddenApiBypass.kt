package org.lsposed.hiddenapibypass

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Base64
import android.util.Log
import dalvik.system.DexFile
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
        InMemoryDexClassLoader(bufferX, null).loadClass("MyVMRuntime")
        val unsafe = Unsafe::class.java.getDeclaredMethod("getUnsafe").invoke(null) as Unsafe
        val dexCacheClass = when (Build.VERSION.SDK_INT + Build.VERSION.PREVIEW_SDK_INT) {
            Build.VERSION_CODES.R, 31 ->
                Helper.DexCacheR::class.java
            Build.VERSION_CODES.Q ->
                Helper.DexCacheQ::class.java
            Build.VERSION_CODES.P ->
                Helper.DexCacheP::class.java
            else -> return true
        }
        val mhClass = when (Build.VERSION.SDK_INT + Build.VERSION.PREVIEW_SDK_INT) {
            else -> Helper.MethodHandle::class.java
        }
        val mhiClass = when (Build.VERSION.SDK_INT + Build.VERSION.PREVIEW_SDK_INT) {
            else ->
                Helper.MethodHandleImpl::class.java
        }
        val artOffset =
            unsafe.objectFieldOffset(mhClass.getDeclaredField("artFieldOrMethod"))
        val infoOffset =
            unsafe.objectFieldOffset(mhiClass.getDeclaredField("info"))
        val methodOffset =
            unsafe.objectFieldOffset(dexCacheClass.getDeclaredField("resolvedMethods"))
        val methodCntOffset =
            unsafe.objectFieldOffset(dexCacheClass.getDeclaredField("numResolvedMethods"))
        val memberOffset =
            unsafe.objectFieldOffset(Helper.HandleInfo::class.java.getDeclaredField("member"))

//        Log.d(TAG, "offset: $artOffset, $methodOffset, $methodCntOffset")
        val dexCache = unsafe.getObject(InMemoryDexClassLoader::class.java, 16)
        try {
            VMRuntime.getRuntime().setHiddenApiExemptions(arrayOf(""))
        } catch (e: Throwable) {
        }
//        val vmDexFile = unsafe.getLong(vmDexCache, 16)

        val cnt = unsafe.getInt(dexCache, methodCntOffset)
        val methods = unsafe.getLong(dexCache, methodOffset)
        var dexFileInit: Constructor<*>? = null;
        var loadBinaryClass: Method? = null
        var defineClass: Method? = null
        for (i in 0 until cnt) {
            val method = if (unsafe.addressSize() == 4) {
                val idx = unsafe.getInt(methods + i * 8 + 4)
                if (idx == if (i == 0) 1 else 0) continue
                unsafe.getInt(methods + i * 8).toLong()
            } else {
                val idx = unsafe.getLong(methods + i * 16 + 8)
                if (idx == if (i == 0) 1L else 0L) continue
                unsafe.getLong(methods + i * 16)
            }
            unsafe.putLong(mh, artOffset, method)
            unsafe.putObject(mh, infoOffset, null)
            try {
                MethodHandles.lookup().revealDirect(mh)
            } catch (e: Throwable) {
            }
            val info = unsafe.getObject(mh, infoOffset) as MethodHandleInfo
            Log.d(TAG, "${info.declaringClass}.${info.name}")
            if (info.declaringClass.name == "dalvik.system.DexFile") {
                when (info.name) {
                    "<init>" -> {
                        val member = unsafe.getObject(info, memberOffset) as Constructor<*>
                        if (member.parameterTypes.isNotEmpty() && (member.parameterTypes[0].isArray || member.parameterTypes[0] == ByteBuffer::class.java)) {
                            member.isAccessible = true
                            dexFileInit = member
                        }
                    }
                    "loadClassBinaryName" -> {
                        val member = unsafe.getObject(info, memberOffset) as Method
                        member.isAccessible = true
                        loadBinaryClass = member
                    }
                    "defineClass" -> {
                        val member = unsafe.getObject(info, memberOffset) as Method
                        member.isAccessible = true
                        defineClass = member
                    }
                }
            }

        }

        Log.d(TAG, dexFileInit?.toString() ?: "null")
        Log.d(TAG, loadBinaryClass?.toString() ?: "null")
        Log.d(TAG, defineClass?.toString() ?: "null")

        return if (dexFileInit != null && (loadBinaryClass != null || defineClass != null)) {
            try {
                val dex = if (dexFileInit.parameterTypes[0].isArray)
                    dexFileInit.newInstance(arrayOf(bufferX), null, null)
                else
                    dexFileInit.newInstance(bufferX)
                val myVm = if (loadBinaryClass != null) {
                    loadBinaryClass.invoke(dex, "MyVMRuntime", null, null)!! as Class<*>
                } else defineClass?.invoke(
                    null,
                    "MyVMRuntime",
                    null,
                    unsafe.getObject(
                        dex,
                        unsafe.objectFieldOffset(Helper.DexFile::class.java.getDeclaredField("mCookie"))
                    ),
                    dex,
                    null
                ) as Class<*>?
                myVm?.getDeclaredMethod("setHiddenApiExemptions", arrayOf("").javaClass)
                    ?.invoke(null, arrayOf("Landroid/"))
                true
            } catch (e: Throwable) {
                Log.e(TAG, "fail", e)
                false
            }
        } else false
    }
}
