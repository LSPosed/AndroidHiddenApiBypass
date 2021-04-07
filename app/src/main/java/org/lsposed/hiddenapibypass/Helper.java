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

    static final public class HandleInfo {
        private final Member member = null;
        private final MethodHandle handle = null;
    }

    static final public class DexCache {
        /**
         * The classloader this dex cache is for.
         */
        private ClassLoader classLoader;

        /**
         * The location of the associated dex file.
         */
        private String location;

        /**
         * Holds C pointer to dexFile.
         */
        private long dexFile;

        /**
         * References to pre resolved strings.
         */
        private long preResolvedStrings;

        /**
         * References to CallSite (C array pointer) as they become resolved following
         * interpreter semantics.
         */
        private long resolvedCallSites;

        /**
         * References to fields (C array pointer) as they become resolved following
         * interpreter semantics. May refer to fields defined in other dex files.
         */
        private long resolvedFields;

        /**
         * References to MethodType (C array pointer) as they become resolved following
         * interpreter semantics.
         */
        private long resolvedMethodTypes;

        /**
         * References to methods (C array pointer) as they become resolved following
         * interpreter semantics. May refer to methods defined in other dex files.
         */
        private long resolvedMethods;

        /**
         * References to types (C array pointer) as they become resolved following
         * interpreter semantics. May refer to types defined in other dex files.
         */
        private long resolvedTypes;

        /**
         * References to strings (C array pointer) as they become resolved following
         * interpreter semantics. All strings are interned.
         */
        private long strings;

        /**
         * The number of elements in the native pre resolved strings array.
         */
        private int numPreResolvedStrings;

        /**
         * The number of elements in the native call sites array.
         */
        private int numResolvedCallSites;

        /**
         * The number of elements in the native resolvedFields array.
         */
        private int numResolvedFields;

        /**
         * The number of elements in the native method types array.
         */
        private int numResolvedMethodTypes;

        /**
         * The number of elements in the native resolvedMethods array.
         */
        private int numResolvedMethods;

        /**
         * The number of elements in the native resolvedTypes array.
         */
        private int numResolvedTypes;

        /**
         * The number of elements in the native strings array.
         */
        private int numStrings;

        // Only created by the VM.
        private DexCache() {
        }
    }

    public native void setHiddenApiExemptions(String[] signaturePrefixes);
}
