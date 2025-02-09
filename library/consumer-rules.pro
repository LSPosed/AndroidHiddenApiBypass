-dontwarn dalvik.system.VMRuntime

-if class org.lsposed.hiddenapibypass.HiddenApiBypass
-keepclassmembers class org.lsposed.hiddenapibypass.Helper$* { *; }

-assumenosideeffects class android.util.Property{
    public static *** of(...);
}
