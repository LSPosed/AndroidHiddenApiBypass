-keep,allowoptimization class org.lsposed.hiddenapibypass.HiddenApiBypass {
    java.util.List getDeclaredMethods(java.lang.Class);
    boolean setHiddenApiExemptions(java.lang.String[]);
}
-keep class org.lsposed.hiddenapibypass.Helper$* { *; }
-keepattributes *Annotations
