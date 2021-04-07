package sun.misc;

public class Unsafe {
    public static Unsafe getUnsafe(){
        throw new IllegalArgumentException("stub");
    }

    public native long getLong(Object obj, long offset);
    public native void putLong(Object obj, long offset, long newValue);
    public native int getInt(Object obj, long offset);
    public native Object getObject(Object obj, long offset);
    public native void putObject(Object obj, long offset, Object newValue);
    public native byte getByte(long address);
    public native void putByte(long address, byte x);
    public native int addressSize();
    public native int getInt(long address);
    public native long getLong(long address);
    public long objectFieldOffset(java.lang.reflect.Field field) {
        throw new RuntimeException("Stub!");
    }
}
