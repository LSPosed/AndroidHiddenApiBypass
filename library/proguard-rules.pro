-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

-keep,allowoptimization class org.lsposed.hiddenapibypass.HiddenApiBypass {
    java.util.List getDeclaredMethods(java.lang.Class);
    boolean setHiddenApiExemptions(java.lang.String[]);
}
-keepclassmembers class org.lsposed.hiddenapibypass.Helper$* { *; }
