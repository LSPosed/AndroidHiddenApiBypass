-keep,allowoptimization class org.lsposed.hiddenapibypass.HiddenApiBypass {
    java.util.List getDeclaredMethods(java.lang.Class);
    java.lang.Object invoke(java.lang.Class, java.lang.Object, java.lang.String, java.lang.Object[]);
    java.lang.Object newInstance(java.lang.Class, java.lang.Object[]);
    boolean setHiddenApiExemptions(java.lang.String[]);
    boolean addHiddenApiExemptions(java.lang.String[]);
    boolean clearHiddenApiExemptions();
}
-keep class org.lsposed.hiddenapibypass.Helper$* { *; }
-keepattributes *Annotations
