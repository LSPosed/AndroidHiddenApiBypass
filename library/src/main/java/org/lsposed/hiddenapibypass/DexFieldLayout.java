/*
 * Copyright (C) 2021-2025 LSPosed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lsposed.hiddenapibypass;

import android.os.Build;
import android.os.SharedMemory;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.zip.ZipException;

@RequiresApi(Build.VERSION_CODES.P)
final class DexFieldLayout {
    static final String OBJECT = descriptorString(Object.class);
    static final String CLASS = descriptorString(Class.class);
    static final String ACCESSIBLE_OBJECT = descriptorString(AccessibleObject.class);
    static final String EXECUTABLE = descriptorString(Executable.class);
    static final String FIELD = descriptorString(Field.class);
    static final String METHOD_HANDLE = descriptorString(MethodHandle.class);
    private static final int OBJECT_HEADER_SIZE = 8;
    private static final int REFERENCE_SIZE = 4;

    private final Set<String> wantedDescriptors = new HashSet<>();
    private final Map<String, DexClass> classes = new HashMap<>();
    private final Map<String, Layout> layouts = new HashMap<>();

    DexFieldLayout() {
        want(CLASS);
        want(ACCESSIBLE_OBJECT);
        want(EXECUTABLE);
        want(METHOD_HANDLE);
    }

    DexFieldLayout want(String descriptor) {
        wantedDescriptors.add(descriptor);
        return this;
    }

    void scanPath(String path) throws IOException {
        if (path.isEmpty()) return;
        var file = Paths.get(path);
        if (!Files.isRegularFile(file)) return;

        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            var mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            mapped.order(ByteOrder.LITTLE_ENDIAN);
            var zipReader = new ZipReader(mapped);
            for (int i = 1; !hasAllClasses(); ++i) {
                var dex = zipReader.getEntry(i == 1 ? "classes.dex" : "classes" + i + ".dex");
                if (dex == null) break;
                new DexReader(dex).scan(classes, wantedDescriptors);
            }
            SharedMemory.unmap(mapped);
        }
    }

    static Map<String, String> findDeclaredFieldTypes(String path, String descriptor)
            throws IOException {
        if (path.isEmpty()) return null;
        var file = Paths.get(path);
        if (!Files.isRegularFile(file)) return null;

        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            var mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            mapped.order(ByteOrder.LITTLE_ENDIAN);
            try {
                if (mapped.limit() >= 0x70 && mapped.getInt(0) == 0x0a786564) {
                    return new DexReader(mapped).findDeclaredFieldTypes(descriptor);
                }
                var zipReader = new ZipReader(mapped);
                for (int i = 1; ; ++i) {
                    var dex = zipReader.getEntry(i == 1 ? "classes.dex" : "classes" + i + ".dex");
                    if (dex == null) return null;
                    Map<String, String> fields = new DexReader(dex)
                            .findDeclaredFieldTypes(descriptor);
                    if (fields != null) return fields;
                }
            } finally {
                SharedMemory.unmap(mapped);
            }
        }
    }

    private boolean hasAllClasses() {
        for (String descriptor : wantedDescriptors) {
            if (!classes.containsKey(descriptor)) return false;
        }
        return true;
    }

    private static String descriptorString(Class<?> clazz) {
        return 'L' + clazz.getName().replace('.', '/') + ';';
    }

    private static ByteBuffer slice(ByteBuffer buffer, int offset, int size) {
        var duplicate = buffer.duplicate();
        duplicate.position(offset);
        duplicate.limit(offset + size);
        return duplicate.slice().order(buffer.order());
    }

    Layout layoutOf(String descriptor) throws ClassNotFoundException {
        if (OBJECT.equals(descriptor)) {
            return new Layout(OBJECT_HEADER_SIZE, new HashMap<>());
        }
        Layout cached = layouts.get(descriptor);
        if (cached != null) return cached;

        DexClass dexClass = classes.get(descriptor);
        if (dexClass == null) throw new ClassNotFoundException(descriptor);
        Layout superLayout = layoutOf(dexClass.superDescriptor);

        ArrayList<DexField> fields = new ArrayList<>(dexClass.instanceFields);
        fields.sort(FIELD_COMPARATOR);

        LinkState state = new LinkState(superLayout.objectSize);
        PriorityQueue<FieldGap> gaps = new PriorityQueue<>(FIELD_GAP_COMPARATOR);
        Map<String, Integer> offsets = new HashMap<>();

        while (state.index < fields.size()) {
            DexField field = fields.get(state.index);
            if (field.isPrimitive()) break;
            if (!isAligned(state.offset, REFERENCE_SIZE)) {
                int oldOffset = state.offset;
                state.offset = roundUp(state.offset, REFERENCE_SIZE);
                addFieldGap(oldOffset, state.offset, gaps);
            }
            offsets.put(field.name, state.offset);
            state.offset += REFERENCE_SIZE;
            state.index++;
        }

        shuffleForward(8, state, fields, gaps, offsets);
        shuffleForward(4, state, fields, gaps, offsets);
        shuffleForward(2, state, fields, gaps, offsets);
        shuffleForward(1, state, fields, gaps, offsets);

        Layout layout = new Layout(state.offset, offsets);
        layouts.put(descriptor, layout);
        return layout;
    }

    private static final Comparator<DexField> FIELD_COMPARATOR = (field1, field2) -> {
        char type1 = field1.primitiveType();
        char type2 = field2.primitiveType();
        if (type1 != type2) {
            if (type1 == 0) return -1;
            if (type2 == 0) return 1;
            int size1 = componentSize(type1);
            int size2 = componentSize(type2);
            if (size1 != size2) return size2 - size1;
            return primitiveOrder(type1) - primitiveOrder(type2);
        }
        return field1.fieldIndex - field2.fieldIndex;
    };

    private static final Comparator<FieldGap> FIELD_GAP_COMPARATOR = (gap1, gap2) -> {
        if (gap1.size != gap2.size) return gap2.size - gap1.size;
        return gap1.startOffset - gap2.startOffset;
    };

    private static void shuffleForward(int size, LinkState state, ArrayList<DexField> fields,
                                       PriorityQueue<FieldGap> gaps, Map<String, Integer> offsets) {
        while (state.index < fields.size()) {
            DexField field = fields.get(state.index);
            if (field.componentSize() < size) break;
            if (!isAligned(state.offset, size)) {
                int oldOffset = state.offset;
                state.offset = roundUp(state.offset, size);
                addFieldGap(oldOffset, state.offset, gaps);
            }

            FieldGap gap = gaps.peek();
            if (gap != null && gap.size >= size) {
                gaps.poll();
                offsets.put(field.name, gap.startOffset);
                if (gap.size > size) {
                    addFieldGap(gap.startOffset + size, gap.startOffset + gap.size, gaps);
                }
            } else {
                offsets.put(field.name, state.offset);
                state.offset += size;
            }
            state.index++;
        }
    }

    private static void addFieldGap(int gapStart, int gapEnd, PriorityQueue<FieldGap> gaps) {
        int offset = gapStart;
        while (offset != gapEnd) {
            int remaining = gapEnd - offset;
            if (remaining >= 4 && isAligned(offset, 4)) {
                gaps.add(new FieldGap(offset, 4));
                offset += 4;
            } else if (remaining >= 2 && isAligned(offset, 2)) {
                gaps.add(new FieldGap(offset, 2));
                offset += 2;
            } else {
                gaps.add(new FieldGap(offset, 1));
                offset += 1;
            }
        }
    }

    private static boolean isAligned(int value, int alignment) {
        return (value & (alignment - 1)) == 0;
    }

    private static int roundUp(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }

    private static int primitiveOrder(char type) {
        switch (type) {
            case 'Z':
                return 1;
            case 'B':
                return 2;
            case 'C':
                return 3;
            case 'S':
                return 4;
            case 'I':
                return 5;
            case 'J':
                return 6;
            case 'F':
                return 7;
            case 'D':
                return 8;
            default:
                return 0;
        }
    }

    private static int componentSize(char type) {
        switch (type) {
            case 'J':
            case 'D':
                return 8;
            case 'I':
            case 'F':
                return 4;
            case 'C':
            case 'S':
                return 2;
            case 'Z':
            case 'B':
                return 1;
            default:
                return REFERENCE_SIZE;
        }
    }

    private static final class ZipReader {
        private static final int EOCD_SIGNATURE = 0x06054b50;
        private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
        private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
        private static final int STORED = 0;
        private static final int MAX_EOCD_SEARCH = 0xffff + 22;

        private final ByteBuffer fileData;
        private final int centralDirectoryOffset;
        private final int entryCount;

        private ZipReader(ByteBuffer fileData) throws ZipException {
            this.fileData = fileData;
            int endOfCentralDirectoryOffset = findEndOfCentralDirectory();
            entryCount = readUnsignedShort(endOfCentralDirectoryOffset + 10);
            centralDirectoryOffset = readInt(endOfCentralDirectoryOffset + 16);
        }

        private ByteBuffer getEntry(String name) throws IOException {
            int offset = centralDirectoryOffset;
            for (int i = 0; i < entryCount; ++i) {
                if (readInt(offset) != CENTRAL_DIRECTORY_SIGNATURE) {
                    throw new ZipException();
                }

                int compressionMethod = readUnsignedShort(offset + 10);
                int compressedSize = readInt(offset + 20);
                int uncompressedSize = readInt(offset + 24);
                int fileNameLength = readUnsignedShort(offset + 28);
                int extraLength = readUnsignedShort(offset + 30);
                int commentLength = readUnsignedShort(offset + 32);
                int localHeaderOffset = readInt(offset + 42);
                int fileNameOffset = offset + 46;
                if (matchesName(fileNameOffset, fileNameLength, name)) {
                    return openEntry(localHeaderOffset, compressionMethod, compressedSize, uncompressedSize);
                }
                offset = fileNameOffset + fileNameLength + extraLength + commentLength;
            }
            return null;
        }

        private ByteBuffer openEntry(int localHeaderOffset, int compressionMethod, int compressedSize,
                                     int uncompressedSize) throws IOException {
            if (readInt(localHeaderOffset) != LOCAL_FILE_HEADER_SIGNATURE) {
                throw new ZipException();
            }
            int fileNameLength = readUnsignedShort(localHeaderOffset + 26);
            int extraLength = readUnsignedShort(localHeaderOffset + 28);
            int dataOffset = localHeaderOffset + 30 + fileNameLength + extraLength;
            if (compressionMethod != STORED || compressedSize != uncompressedSize) {
                throw new ZipException();
            }
            return slice(fileData, dataOffset, uncompressedSize);
        }

        private int findEndOfCentralDirectory() throws ZipException {
            if (fileData.limit() < 22) {
                throw new ZipException();
            }
            int start = Math.max(0, fileData.limit() - MAX_EOCD_SEARCH);
            for (int offset = fileData.limit() - 22; offset >= start; --offset) {
                if (readInt(offset) == EOCD_SIGNATURE) {
                    return offset;
                }
            }
            throw new ZipException();
        }

        private boolean matchesName(int offset, int length, String expected) {
            if (length != expected.length()) return false;
            for (int i = 0; i < length; ++i) {
                if ((char) (fileData.get(offset + i) & 0xff) != expected.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private int readInt(int offset) {
            return fileData.getInt(offset);
        }

        private int readUnsignedShort(int offset) {
            return fileData.getShort(offset) & 0xffff;
        }
    }

    private static final class DexReader {
        private static final int NO_INDEX = -1;
        private final ByteBuffer dex;
        private final int stringIdsSize;
        private final int stringIdsOff;
        private final int typeIdsSize;
        private final int typeIdsOff;
        private final int fieldIdsSize;
        private final int fieldIdsOff;
        private final int classDefsSize;
        private final int classDefsOff;

        private DexReader(ByteBuffer dex) {
            this.dex = dex;
            if (dex.limit() < 0x70 || readInt(0) != 0x0a786564) {
                throw new IllegalArgumentException("Not a dex file");
            }
            stringIdsSize = readInt(0x38);
            stringIdsOff = readInt(0x3c);
            typeIdsSize = readInt(0x40);
            typeIdsOff = readInt(0x44);
            fieldIdsSize = readInt(0x50);
            fieldIdsOff = readInt(0x54);
            classDefsSize = readInt(0x60);
            classDefsOff = readInt(0x64);
        }

        private void scan(Map<String, DexClass> classes, Set<String> wantedDescriptors) {
            Map<Integer, String> wantedTypes = new HashMap<>();
            for (String descriptor : wantedDescriptors) {
                addWantedType(classes, wantedTypes, descriptor);
            }
            if (wantedTypes.isEmpty()) return;

            for (int i = 0; i < classDefsSize && !wantedTypes.isEmpty(); ++i) {
                int offset = classDefsOff + i * 32;
                String descriptor = wantedTypes.remove(readInt(offset));
                if (descriptor == null) continue;
                int superclassIndex = readInt(offset + 8);
                String superDescriptor = superclassIndex == NO_INDEX ? OBJECT : getTypeDescriptor(superclassIndex);
                int classDataOff = readInt(offset + 24);
                classes.put(descriptor, new DexClass(superDescriptor, readInstanceFields(classDataOff)));
            }
        }

        private Map<String, String> findDeclaredFieldTypes(String descriptor) {
            int classIndex = findTypeIndex(descriptor);
            if (classIndex < 0) return null;

            for (int i = 0; i < classDefsSize; ++i) {
                int offset = classDefsOff + i * 32;
                if (readInt(offset) != classIndex) continue;
                return readDeclaredFieldTypes(readInt(offset + 24));
            }
            return null;
        }

        private void addWantedType(Map<String, DexClass> classes, Map<Integer, String> wantedTypes, String descriptor) {
            if (classes.containsKey(descriptor)) return;
            int typeIndex = findTypeIndex(descriptor);
            if (typeIndex >= 0) wantedTypes.put(typeIndex, descriptor);
        }

        private ArrayList<DexField> readInstanceFields(int offset) {
            ArrayList<DexField> fields = new ArrayList<>();
            if (offset == 0) return fields;

            Position position = new Position(offset);
            int staticFieldsSize = readUleb128(position);
            int instanceFieldsSize = readUleb128(position);
            int directMethodsSize = readUleb128(position);
            int virtualMethodsSize = readUleb128(position);

            skipFields(position, staticFieldsSize);
            int fieldIndex = 0;
            for (int i = 0; i < instanceFieldsSize; ++i) {
                fieldIndex += readUleb128(position);
                readUleb128(position);
                fields.add(readField(fieldIndex));
            }
            skipMethods(position, directMethodsSize + virtualMethodsSize);
            return fields;
        }

        private Map<String, String> readDeclaredFieldTypes(int offset) {
            Map<String, String> fields = new HashMap<>();
            if (offset == 0) return fields;

            Position position = new Position(offset);
            int staticFieldsSize = readUleb128(position);
            int instanceFieldsSize = readUleb128(position);
            int directMethodsSize = readUleb128(position);
            int virtualMethodsSize = readUleb128(position);

            readFieldTypes(position, staticFieldsSize, fields);
            readFieldTypes(position, instanceFieldsSize, fields);
            skipMethods(position, directMethodsSize + virtualMethodsSize);
            return fields;
        }

        private void readFieldTypes(Position position, int count, Map<String, String> fields) {
            int fieldIndex = 0;
            for (int i = 0; i < count; ++i) {
                fieldIndex += readUleb128(position);
                readUleb128(position);
                DexField field = readField(fieldIndex);
                fields.put(field.name, field.type);
            }
        }

        private void skipFields(Position position, int count) {
            for (int i = 0; i < count; ++i) {
                readUleb128(position);
                readUleb128(position);
            }
        }

        private void skipMethods(Position position, int count) {
            for (int i = 0; i < count; ++i) {
                readUleb128(position);
                readUleb128(position);
                readUleb128(position);
            }
        }

        private DexField readField(int fieldIndex) {
            if (fieldIndex < 0 || fieldIndex >= fieldIdsSize) {
                throw new IllegalArgumentException("Invalid field index " + fieldIndex);
            }
            int offset = fieldIdsOff + fieldIndex * 8;
            int typeIndex = readUnsignedShort(offset + 2);
            int nameIndex = readInt(offset + 4);
            return new DexField(fieldIndex, getString(nameIndex), getTypeDescriptor(typeIndex));
        }

        private String getTypeDescriptor(int typeIndex) {
            if (typeIndex < 0 || typeIndex >= typeIdsSize) {
                throw new IllegalArgumentException("Invalid type index " + typeIndex);
            }
            return getString(readInt(typeIdsOff + typeIndex * 4));
        }

        private int findTypeIndex(String descriptor) {
            int descriptorIndex = findStringIndex(descriptor);
            if (descriptorIndex < 0) return -1;

            int low = 0;
            int high = typeIdsSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int actual = readInt(typeIdsOff + mid * 4);
                if (actual < descriptorIndex) {
                    low = mid + 1;
                } else if (actual > descriptorIndex) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            }
            return -1;
        }

        private int findStringIndex(String expected) {
            int low = 0;
            int high = stringIdsSize - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int compare = compareString(mid, expected);
                if (compare < 0) {
                    low = mid + 1;
                } else if (compare > 0) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            }
            return -1;
        }

        private int compareString(int stringIndex, String expected) {
            if (stringIndex < 0 || stringIndex >= stringIdsSize) {
                throw new IllegalArgumentException("Invalid string index " + stringIndex);
            }
            Position position = new Position(readInt(stringIdsOff + stringIndex * 4));
            readUleb128(position);
            int expectedIndex = 0;
            while (true) {
                int actual = readModifiedUtf8Char(position);
                if (actual == 0) {
                    return expectedIndex == expected.length() ? 0 : -1;
                }
                if (expectedIndex == expected.length()) {
                    return 1;
                }
                int expectedChar = expected.charAt(expectedIndex++);
                if (actual != expectedChar) {
                    return actual - expectedChar;
                }
            }
        }

        private int readModifiedUtf8Char(Position position) {
            int current = dex.get(position.offset++) & 0xff;
            if (current == 0 || (current & 0x80) == 0) {
                return current;
            }
            if ((current & 0xe0) == 0xc0) {
                return ((current & 0x1f) << 6) | (dex.get(position.offset++) & 0x3f);
            }
            return ((current & 0x0f) << 12)
                    | ((dex.get(position.offset++) & 0x3f) << 6)
                    | (dex.get(position.offset++) & 0x3f);
        }

        private String getString(int stringIndex) {
            if (stringIndex < 0 || stringIndex >= stringIdsSize) {
                throw new IllegalArgumentException("Invalid string index " + stringIndex);
            }
            Position position = new Position(readInt(stringIdsOff + stringIndex * 4));
            readUleb128(position);
            int start = position.offset;
            while (position.offset < dex.limit() && dex.get(position.offset) != 0) {
                position.offset++;
            }
            return StandardCharsets.UTF_8.decode(slice(dex, start, position.offset - start)).toString();
        }

        private int readUleb128(Position position) {
            int result = 0;
            int shift = 0;
            int current;
            do {
                current = dex.get(position.offset++) & 0xff;
                result |= (current & 0x7f) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);
            return result;
        }

        private int readInt(int offset) {
            return dex.getInt(offset);
        }

        private int readUnsignedShort(int offset) {
            return dex.getShort(offset) & 0xffff;
        }
    }

    private static final class DexClass {
        private final String superDescriptor;
        private final ArrayList<DexField> instanceFields;

        private DexClass(String superDescriptor, ArrayList<DexField> instanceFields) {
            this.superDescriptor = superDescriptor;
            this.instanceFields = instanceFields;
        }
    }

    private static final class DexField {
        private final int fieldIndex;
        private final String name;
        private final String type;

        private DexField(int fieldIndex, String name, String type) {
            this.fieldIndex = fieldIndex;
            this.name = name;
            this.type = type;
        }

        private char primitiveType() {
            return type.length() == 1 ? type.charAt(0) : 0;
        }

        private boolean isPrimitive() {
            return primitiveType() != 0;
        }

        private int componentSize() {
            return DexFieldLayout.componentSize(primitiveType());
        }
    }

    static final class Layout {
        private final int objectSize;
        private final Map<String, Integer> offsets;

        private Layout(int objectSize, Map<String, Integer> offsets) {
            this.objectSize = objectSize;
            this.offsets = offsets;
        }

        boolean hasField(String name) {
            return offsets.containsKey(name);
        }

        int offsetOf(String name) throws NoSuchFieldException {
            Integer offset = offsets.get(name);
            if (offset == null) throw new NoSuchFieldException(name);
            return offset;
        }
    }

    private static final class FieldGap {
        private final int startOffset;
        private final int size;

        private FieldGap(int startOffset, int size) {
            this.startOffset = startOffset;
            this.size = size;
        }
    }

    private static final class LinkState {
        private int index;
        private int offset;

        private LinkState(int offset) {
            this.offset = offset;
        }
    }

    private static final class Position {
        private int offset;

        private Position(int offset) {
            this.offset = offset;
        }
    }
}
