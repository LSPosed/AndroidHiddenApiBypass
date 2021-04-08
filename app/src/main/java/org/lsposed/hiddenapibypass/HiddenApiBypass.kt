package org.lsposed.hiddenapibypass

import android.util.Log
import dalvik.system.VMRuntime
import sun.misc.Unsafe
import java.lang.invoke.MethodHandleInfo
import java.lang.invoke.MethodHandles
import java.lang.reflect.Executable
import java.lang.reflect.Method

object HiddenApiBypass {
    @JvmStatic
    external fun setHiddenApiExemptions(vararg signaturePrefixes: String): Boolean
    const val TAG = "HiddenApiBypass"

    fun nativeBypass(vararg signaturePrefixes: String): Boolean {
        return setHiddenApiExemptions(*signaturePrefixes);
    }

    private val unsafe: Unsafe = Unsafe::class.java.getDeclaredMethod("getUnsafe")(null) as Unsafe
    private val artOffset: Long =
        unsafe.objectFieldOffset(Helper.MethodHandle::class.java.getDeclaredField("artFieldOrMethod"))
    private val infoOffset: Long =
        unsafe.objectFieldOffset(Helper.MethodHandleImpl::class.java.getDeclaredField("info"))
    private val methodsOffset: Long =
        unsafe.objectFieldOffset(Helper.Class::class.java.getDeclaredField("methods"))
    private val memberOffset: Long =
        unsafe.objectFieldOffset(Helper.HandleInfo::class.java.getDeclaredField("member"))
    private val size: Long
    private val bias: Long

    init {
        val mhA =
            MethodHandles.lookup().unreflect(Helper.NeverCall::class.java.getDeclaredMethod("a"))
        val mhB =
            MethodHandles.lookup().unreflect(Helper.NeverCall::class.java.getDeclaredMethod("b"))

        val aAddr = unsafe.getLong(mhA, artOffset)
        val bAddr = unsafe.getLong(mhB, artOffset)
        val aMethods = unsafe.getLong(Helper.NeverCall::class.java, methodsOffset)
        size = bAddr - aAddr
        Log.d(TAG, "$size ${aAddr.toString(16)}, ${bAddr.toString(16)}, ${aMethods.toString(16)}")
        bias = aAddr - aMethods - size
    }

    fun getDeclaredMethods(clazz: Class<*>): List<Executable> {
        val mh =
            MethodHandles.lookup().unreflect(Helper.NeverCall::class.java.getDeclaredMethod("a"))
        val methods = unsafe.getLong(VMRuntime::class.java, methodsOffset)
        val numMethods = unsafe.getInt(methods)
        Log.d(TAG, "$clazz has $numMethods methods")
        return (0 until numMethods).map {
            val method = methods + it * size + bias
            unsafe.putLong(mh, artOffset, method)
            unsafe.putObject(mh, infoOffset, null)
            try {
                MethodHandles.lookup().revealDirect(mh)
            } catch (e: Throwable) {
            }
            val info = unsafe.getObject(mh, infoOffset) as MethodHandleInfo
            val member = unsafe.getObject(info, memberOffset) as Executable
            Log.d(TAG, "got $clazz.$member")
            member
        }

    }

    fun javaBypass(vararg signaturePrefixes: String): Boolean {
        val methods = getDeclaredMethods(VMRuntime::class.java)
        val getRuntime = methods.firstOrNull {
            it.name == "getRuntime"
        } ?: return false
        val setHiddenApiExemptions = methods.firstOrNull {
            it.name == "setHiddenApiExemptions"
        } ?: return false
        getRuntime.isAccessible = true
        val runtime = (getRuntime as Method)(null)
        setHiddenApiExemptions.isAccessible = true
        (setHiddenApiExemptions as Method)(runtime, signaturePrefixes)
        return true
    }
}
