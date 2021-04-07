package sun.misc;

public class Unsafe {
    public static Unsafe getUnsafe(){
        throw new IllegalArgumentException("stub");
    }

    public native long getLong(Object obj, long offset);
    public native Object getObject(Object obj, long offset);
    public native byte getByte(long address);
    public native void putByte(long address, byte x);
}
