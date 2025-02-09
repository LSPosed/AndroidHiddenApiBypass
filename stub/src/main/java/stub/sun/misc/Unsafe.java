package stub.sun.misc;

@SuppressWarnings({"unused", "rawtypes"})
public class Unsafe {
    public native long getLong(Object obj, long offset);
    public native void putLong(Object obj, long offset, long newValue);
    public native int getInt(Object obj, long offset);
    public native void putInt(java.lang.Object obj, long offset, int newValue);
    public native short getShort(java.lang.Object obj, long offset);
    public native Object getObject(Object obj, long offset);
    public native void putObject(Object obj, long offset, Object newValue);
    public native byte getByte(long address);
    public native void putByte(long address, byte x);
    public native int addressSize();
    public native int getInt(long address);
    public native long getLong(long address);
    public int arrayBaseOffset(Class clazz) {
        throw new RuntimeException("Stub!");
    }
    public long objectFieldOffset(java.lang.reflect.Field field) {
        throw new RuntimeException("Stub!");
    }
}
