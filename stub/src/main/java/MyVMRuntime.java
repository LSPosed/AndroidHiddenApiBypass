import dalvik.system.VMRuntime;

public class MyVMRuntime {
    public static void setHiddenApiExemptions(String[] signaturePrefixes) {
        VMRuntime.getRuntime().setHiddenApiExemptions(signaturePrefixes);
    }
}
