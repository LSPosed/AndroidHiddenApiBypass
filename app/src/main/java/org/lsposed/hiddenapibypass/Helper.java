package org.lsposed.hiddenapibypass;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Member;

public class Helper {
    static public class MethodHandle {
        private final MethodType type = null;
        private MethodType nominalType;
        private MethodHandle cachedSpreadInvoker;
        protected final int handleKind = 0;

        // The ArtMethod* or ArtField* associated with this method handle (used by the runtime).
        protected final long artFieldOrMethod = 0;
    }

    static final public class MethodHandleImpl extends MethodHandle {
        private MethodHandleInfo info = null;
    }

    static final public class MethodHandleImplS extends MethodHandle {
        private MethodHandleInfo info = null;
    }

    static final public class HandleInfo {
        private final Member member = null;
        private final MethodHandle handle = null;
    }

    static final public class DexCacheP {
        private String location;
        private long dexFile;
        private long resolvedCallSites;
        private long resolvedFields;
        private long resolvedMethodTypes;
        private long resolvedMethods;
        private long resolvedTypes;
        private long strings;
        private int numResolvedCallSites;
        private int numResolvedFields;
        private int numResolvedMethodTypes;
        private int numResolvedMethods;
        private int numResolvedTypes;
        private int numStrings;
    }

    static final public class DexCacheQ {
        private String location;
        private long dexFile;
        private long preResolvedStrings;
        private long resolvedCallSites;
        private long resolvedFields;
        private long resolvedMethodTypes;
        private long resolvedMethods;
        private long resolvedTypes;
        private long strings;
        private int numPreResolvedStrings;
        private int numResolvedCallSites;
        private int numResolvedFields;
        private int numResolvedMethodTypes;
        private int numResolvedMethods;
        private int numResolvedTypes;
        private int numStrings;
    }

    static final public class DexCacheR {
        private ClassLoader classLoader;
        private String location;
        private long dexFile;
        private long preResolvedStrings;
        private long resolvedCallSites;
        private long resolvedFields;
        private long resolvedMethodTypes;
        private long resolvedMethods;
        private long resolvedTypes;
        private long strings;
        private int numPreResolvedStrings;
        private int numResolvedCallSites;
        private int numResolvedFields;
        private int numResolvedMethodTypes;
        private int numResolvedMethods;
        private int numResolvedTypes;
        private int numStrings;
    }

    static final public class DexFile {
        private Object mCookie;
        private Object mInternalCookie;
        private final String mFileName = null;
    }

    public native void setHiddenApiExemptions(String[] signaturePrefixes);
}
