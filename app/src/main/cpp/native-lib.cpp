/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors
 */
#include <jni.h>
#include "elf_util.h"
#include "logging.h"

namespace {
    const char *libart_path() {
        const auto api = android_get_device_api_level();
#ifdef __LP64__
#define LIB_POSTFIX "64"
#else
#define LIB_POSTFIX ""
#endif
        if (api >= __ANDROID_API_R__) {
            return "/apex/com.android.art/lib" LIB_POSTFIX "/libart.so";
        } else if (api == __ANDROID_API_Q__) {
            return "/apex/com.android.runtime/lib" LIB_POSTFIX "/libart.so";
        } else {
            return "/system/lib" LIB_POSTFIX "/libart.so";
        }
#undef LIB_POSTFIX
    }

    void (*setHiddenApiExemptions)(JNIEnv *env,
                                   jclass,
                                   jobjectArray exemptions);

    bool init() {
        if (setHiddenApiExemptions) return true;
        setHiddenApiExemptions = reinterpret_cast<decltype(setHiddenApiExemptions)>(SandHook::ElfImg(
                libart_path()).getSymbAddress(
                "_ZN3artL32VMRuntime_setHiddenApiExemptionsEP7_JNIEnvP7_jclassP13_jobjectArray"));
        LOGD("setHiddenApiExemptions: %p", setHiddenApiExemptions);
        return setHiddenApiExemptions;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_lsposed_hiddenapibypass_HiddenApiBypass_setHiddenApiExemptions(JNIEnv *env, jclass clazz,
                                                                        jobjectArray signature_prefixes) {
    if (android_get_device_api_level() < __ANDROID_API_P__)
        return JNI_TRUE;
    if (init()) {
        setHiddenApiExemptions(env, clazz, signature_prefixes);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
