-keep,allowoptimization class org.lsposed.hiddenapibypass.HiddenApiBypass {
    java.util.List getDeclaredMethods(java.lang.Class);
    boolean setHiddenApiExemptions(java.lang.String[]);
    boolean addHiddenApiExemptions(java.lang.String[]);
    boolean clearHiddenApiExemptions();
}
-keep class org.lsposed.hiddenapibypass.Helper$* { *; }
-keepattributes *Annotations
