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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

final class DexFieldLayout {
    private static final String OBJECT = "Ljava/lang/Object;";
    private static final String CLASS = "Ljava/lang/Class;";
    private static final String ACCESSIBLE_OBJECT = "Ljava/lang/reflect/AccessibleObject;";
    private static final String EXECUTABLE = "Ljava/lang/reflect/Executable;";
    private static final String METHOD_HANDLE = "Ljava/lang/invoke/MethodHandle;";
    private static final int OBJECT_HEADER_SIZE = 8;
    private static final int REFERENCE_SIZE = 4;

    private final Map<String, DexClass> classes = new HashMap<>();
    private final Map<String, Layout> layouts = new HashMap<>();

    static long[] readOffsetData() throws IOException, ReflectiveOperationException {
        DexFieldLayout scanner = new DexFieldLayout();
        String bootClassPath = System.getProperty("java.boot.class.path", "");
        if (bootClassPath == null) bootClassPath = "";
        for (String path : bootClassPath.split(":")) {
            if (scanner.hasAllClasses()) break;
            scanner.scanPath(path);
        }
        if (!scanner.hasAllClasses()) {
            throw new ClassNotFoundException("Missing boot dex classes for offset calculation");
        }

        Layout executable = scanner.layoutOf(EXECUTABLE);
        Layout methodHandle = scanner.layoutOf(METHOD_HANDLE);
        Layout classClass = scanner.layoutOf(CLASS);

        long[] data = new long[6];
        data[0] = executable.offsetOf("artMethod");
        data[1] = executable.offsetOf("declaringClass");
        data[2] = methodHandle.offsetOf("artFieldOrMethod");
        data[3] = classClass.offsetOf("methods");
        if (classClass.hasField("fields")) {
            data[4] = classClass.offsetOf("fields");
            data[5] = data[4];
        } else {
            data[4] = classClass.offsetOf("iFields");
            data[5] = classClass.offsetOf("sFields");
        }
        return data;
    }

    private void scanPath(String path) throws IOException {
        if (path.isEmpty()) return;
        File file = new File(path);
        if (!file.isFile()) return;

        try (ZipFile zipFile = new ZipFile(file)) {
            for (int i = 1; !hasAllClasses(); ++i) {
                ZipEntry entry = zipFile.getEntry(i == 1 ? "classes.dex" : "classes" + i + ".dex");
                if (entry == null) break;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    scanDex(readAllBytes(is, entry.getSize()));
                }
            }
        } catch (ZipException e) {
            try (InputStream is = new FileInputStream(file)) {
                scanDex(readAllBytes(is, file.length()));
            }
        }
    }

    private static byte[] readAllBytes(InputStream is, long size) throws IOException {
        ByteArrayOutputStream os = null;
        if (size >= 0 && size <= Integer.MAX_VALUE) {
            byte[] bytes = new byte[(int) size];
            int offset = 0;
            while (offset < bytes.length) {
                int read = is.read(bytes, offset, bytes.length - offset);
                if (read == -1) break;
                offset += read;
            }
            if (offset == bytes.length) return bytes;
            os = new ByteArrayOutputStream(offset);
            os.write(bytes, 0, offset);
        }

        if (os == null) os = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
        return os.toByteArray();
    }

    private boolean hasAllClasses() {
        return classes.containsKey(CLASS)
                && classes.containsKey(ACCESSIBLE_OBJECT)
                && classes.containsKey(EXECUTABLE)
                && classes.containsKey(METHOD_HANDLE);
    }

    private void scanDex(byte[] dex) {
        new DexReader(dex).scan(classes);
    }

    private Layout layoutOf(String descriptor) throws ClassNotFoundException {
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

    private static final class DexReader {
        private static final int NO_INDEX = -1;
        private final byte[] dex;
        private final int stringIdsSize;
        private final int stringIdsOff;
        private final int typeIdsSize;
        private final int typeIdsOff;
        private final int fieldIdsSize;
        private final int fieldIdsOff;
        private final int classDefsSize;
        private final int classDefsOff;

        private DexReader(byte[] dex) {
            this.dex = dex;
            if (dex.length < 0x70 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
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

        private void scan(Map<String, DexClass> classes) {
            Map<Integer, String> wantedTypes = new HashMap<>();
            addWantedType(classes, wantedTypes, CLASS);
            addWantedType(classes, wantedTypes, ACCESSIBLE_OBJECT);
            addWantedType(classes, wantedTypes, EXECUTABLE);
            addWantedType(classes, wantedTypes, METHOD_HANDLE);
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
            int current = dex[position.offset++] & 0xff;
            if (current == 0 || (current & 0x80) == 0) {
                return current;
            }
            if ((current & 0xe0) == 0xc0) {
                return ((current & 0x1f) << 6) | (dex[position.offset++] & 0x3f);
            }
            return ((current & 0x0f) << 12)
                    | ((dex[position.offset++] & 0x3f) << 6)
                    | (dex[position.offset++] & 0x3f);
        }

        private String getString(int stringIndex) {
            if (stringIndex < 0 || stringIndex >= stringIdsSize) {
                throw new IllegalArgumentException("Invalid string index " + stringIndex);
            }
            Position position = new Position(readInt(stringIdsOff + stringIndex * 4));
            readUleb128(position);
            int start = position.offset;
            while (position.offset < dex.length && dex[position.offset] != 0) {
                position.offset++;
            }
            return new String(dex, start, position.offset - start, StandardCharsets.UTF_8);
        }

        private int readUleb128(Position position) {
            int result = 0;
            int shift = 0;
            int current;
            do {
                current = dex[position.offset++] & 0xff;
                result |= (current & 0x7f) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);
            return result;
        }

        private int readInt(int offset) {
            return (dex[offset] & 0xff)
                    | ((dex[offset + 1] & 0xff) << 8)
                    | ((dex[offset + 2] & 0xff) << 16)
                    | ((dex[offset + 3] & 0xff) << 24);
        }

        private int readUnsignedShort(int offset) {
            return (dex[offset] & 0xff) | ((dex[offset + 1] & 0xff) << 8);
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

    private static final class Layout {
        private final int objectSize;
        private final Map<String, Integer> offsets;

        private Layout(int objectSize, Map<String, Integer> offsets) {
            this.objectSize = objectSize;
            this.offsets = offsets;
        }

        private boolean hasField(String name) {
            return offsets.containsKey(name);
        }

        private int offsetOf(String name) throws NoSuchFieldException {
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
