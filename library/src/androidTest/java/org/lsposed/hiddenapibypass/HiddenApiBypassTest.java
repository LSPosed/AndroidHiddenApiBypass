package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("JavaReflectionMemberAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4.class)
public class HiddenApiBypassTest {

    private final Class<?> runtime = Class.forName("dalvik.system.VMRuntime");
    // Sampled from AOSP android17-release prebuilts/runtime/appcompat/hiddenapi-flags.csv.
    private static final String[][] HIDDEN_API_FIELDS = {
            {"android.content.pm.ApplicationInfo", "longVersionCode"},
            {"android.content.pm.ApplicationInfo", "HIDDEN_API_ENFORCEMENT_DEFAULT"},
            {"android.content.pm.ApplicationInfo", "privateFlags"},
            {"android.content.pm.ApplicationInfo", "primaryCpuAbi"},
            {"android.content.pm.ApplicationInfo", "scanSourceDir"},
            {"android.app.ActivityOptions", "mAnimationType"},
            {"android.app.ActivityOptions", "mLaunchBounds"},
            {"android.app.ActivityOptions", "mPackageName"},
            {"android.app.ActivityOptions", "mHeight"},
            {"android.app.ActivityOptions", "mWidth"},
            {"android.animation.PropertyValuesHolder", "mAnimatedValue"},
            {"android.animation.PropertyValuesHolder", "mValueType"},
            {"android.animation.TypeConverter", "mToClass"},
            {"android.app.ActivityThread", "sCurrentActivityThread"},
            {"android.app.ActivityThread", "mInitialApplication"},
            {"android.app.ActivityThread", "mBoundApplication"},
            {"android.app.ActivityThread", "mH"},
            {"android.app.ActivityThread", "mPackages"},
            {"android.os.Message", "flags"},
            {"android.os.Message", "next"},
            {"android.os.Message", "sPoolSize"},
            {"android.os.UserHandle", "PER_USER_RANGE"},
            {"android.os.UserHandle", "mHandle"},
            {"android.os.UserHandle", "MU_ENABLED"},
            {"android.view.View", "mPrivateFlags"},
            {"android.view.View", "mViewFlags"},
            {"android.view.View", "mLeft"},
            {"android.view.View", "mRight"},
            {"android.view.View", "mTop"},
            {"android.view.View", "mBottom"},
            {"android.view.View", "mAttachInfo"},
            {"android.widget.TextView", "mText"},
            {"android.widget.TextView", "mLayout"},
            {"android.widget.TextView", "mEditor"},
            {"android.widget.TextView", "mTextColor"},
            {"android.widget.TextView", "mCurTextColor"},
            {"android.net.NetworkCapabilities", "mNetworkCapabilities"},
            {"android.net.NetworkCapabilities", "mTransportTypes"},
            {"android.net.NetworkCapabilities", "mLinkUpBandwidthKbps"},
            {"android.net.NetworkCapabilities", "mLinkDownBandwidthKbps"},
            {"android.telephony.TelephonyManager", "sInstance"},
            {"android.telephony.TelephonyManager", "mContext"},
            {"android.telephony.TelephonyManager", "mSubId"},
    };

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public HiddenApiBypassTest() throws ClassNotFoundException {
    }

    @Test
    public void AgetDeclaredMethods() {
        List<Executable> methods = HiddenApiBypass.getDeclaredMethods(runtime);
        Optional<Executable> getRuntime = methods.stream().filter(it -> it.getName().equals("getRuntime")).findFirst();
        assertTrue(getRuntime.isPresent());
        Optional<Executable> setHiddenApiExemptions = methods.stream().filter(it -> it.getName().equals("setHiddenApiExemptions")).findFirst();
        assertTrue(setHiddenApiExemptions.isPresent());
    }

    @Test(expected = NoSuchMethodException.class)
    public void BusesNonSdkApiIsHiddenApi() throws NoSuchMethodException {
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
    }

    @Test(expected = NoSuchMethodException.class)
    public void CsetHiddenApiExemptionsIsHiddenApi() throws NoSuchMethodException {
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void DnewClipDrawableIsHiddenApi() throws NoSuchMethodException {
        ClipDrawable.class.getDeclaredConstructor();
    }

    @Test(expected = NoSuchFieldException.class)
    public void ElongVersionCodeIsHiddenApi() throws NoSuchFieldException {
        ApplicationInfo.class.getDeclaredField("longVersionCode");
    }

    @Test(expected = NoSuchFieldException.class)
    public void FHiddenApiEnforcementDefaultIsHiddenApi() throws NoSuchFieldException {
        ApplicationInfo.class.getDeclaredField("HIDDEN_API_ENFORCEMENT_DEFAULT");
    }

    @Test
    public void GtestGetInstanceFields() {
        assertTrue(HiddenApiBypass.getInstanceFields(ApplicationInfo.class).stream().anyMatch(i -> i.getName().equals("longVersionCode")));
    }

    @Test
    public void HtestGetStaticFields() {
        assertTrue(HiddenApiBypass.getStaticFields(ApplicationInfo.class).stream().anyMatch(i -> i.getName().equals("HIDDEN_API_ENFORCEMENT_DEFAULT")));
    }

    @Test
    public void ItestFieldsFromHiddenApiList() throws ClassNotFoundException {
        assumeTrue(Build.VERSION.SDK_INT >= 37);
        for (var hiddenField : HIDDEN_API_FIELDS) {
            Class<?> clazz = Class.forName(hiddenField[0]);
            boolean found = containsField(HiddenApiBypass.getInstanceFields(clazz), hiddenField[1])
                    || containsField(HiddenApiBypass.getStaticFields(clazz), hiddenField[1]);
            assertTrue(hiddenField[0] + "." + hiddenField[1], found);
        }
    }

    @Test
    public void ItestAllFieldsFromHiddenApiCsv() throws IOException {
        Bundle args = InstrumentationRegistry.getArguments();
        String csv = args.getString("hiddenapiCsv");
        assumeTrue(csv != null && !csv.isEmpty());

        int classes = 0;
        int fields = 0;
        int skippedClasses = 0;
        int missingFields = 0;
        StringBuilder missing = new StringBuilder();

        String currentClass = null;
        Set<String> currentFields = new HashSet<>();
        try (var reader = new BufferedReader(new FileReader(resolveHiddenApiFile(csv)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var field = parseHiddenApiField(line);
                if (field == null) continue;
                if (currentClass != null && !currentClass.equals(field[0])) {
                    var result = checkHiddenApiFields(currentClass, currentFields, missing);
                    classes++;
                    fields += currentFields.size();
                    if (result < 0) skippedClasses++;
                    else missingFields += result;
                    currentFields.clear();
                }
                currentClass = field[0];
                currentFields.add(field[1]);
            }
        }
        if (currentClass != null) {
            var result = checkHiddenApiFields(currentClass, currentFields, missing);
            classes++;
            fields += currentFields.size();
            if (result < 0) skippedClasses++;
            else missingFields += result;
        }

        assertTrue("classes=" + classes + ", fields=" + fields
                + ", skippedClasses=" + skippedClasses
                + ", missingFields=" + missingFields + "\n" + missing, missingFields == 0);
    }

    @Test
    public void ItestExportPresentFieldsFromHiddenApiCsv() throws IOException, NoSuchFieldException {
        Bundle args = InstrumentationRegistry.getArguments();
        String csv = args.getString("hiddenapiCsv");
        String output = args.getString("hiddenapiPresentCsv");
        assumeTrue(csv != null && !csv.isEmpty());
        assumeTrue(output != null && !output.isEmpty());
        assertHiddenApiPolicyPermissive();

        int classes = 0;
        int fields = 0;
        int skippedClasses = 0;
        int presentFields = 0;

        File outputFile = resolveHiddenApiFile(output);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null) {
            assertTrue("Cannot create " + outputDir, outputDir.mkdirs() || outputDir.isDirectory());
        }

        String currentClass = null;
        Map<String, String> currentFields = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new FileReader(resolveHiddenApiFile(csv)));
             var writer = new BufferedWriter(new FileWriter(outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var field = parseHiddenApiField(line);
                if (field == null) continue;
                if (currentClass != null && !currentClass.equals(field[0])) {
                    var result = writePresentHiddenApiFields(currentClass, currentFields, writer);
                    classes++;
                    fields += currentFields.size();
                    if (result < 0) skippedClasses++;
                    else presentFields += result;
                    currentFields.clear();
                }
                currentClass = field[0];
                currentFields.put(field[1], line);
            }
            if (currentClass != null) {
                var result = writePresentHiddenApiFields(currentClass, currentFields, writer);
                classes++;
                fields += currentFields.size();
                if (result < 0) skippedClasses++;
                else presentFields += result;
            }
        }

        assertTrue("classes=" + classes + ", fields=" + fields
                + ", skippedClasses=" + skippedClasses
                + ", presentFields=" + presentFields, presentFields > 0);
    }

    @Test
    public void IinvokeNonSdkApiWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assertNotEquals(HiddenApiBypass.getDeclaredMethod(ApplicationInfo.class, "getHiddenApiEnforcementPolicy"), null);
        HiddenApiBypass.invoke(ApplicationInfo.class, new ApplicationInfo(), "getHiddenApiEnforcementPolicy");
    }

    @Test
    public void JnewClipDrawableWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        assertNotEquals(HiddenApiBypass.getDeclaredConstructor(ClipDrawable.class), null);
        Object instance = HiddenApiBypass.newInstance(ClipDrawable.class);
        assertSame(instance.getClass(), ClipDrawable.class);
    }

    @Test
    public void KgetAllMethodsWithoutExemption() {
        assertTrue(HiddenApiBypass.getDeclaredMethods(ApplicationInfo.class).stream().anyMatch(e -> e.getName().equals("getHiddenApiEnforcementPolicy")));
    }

    @Test
    public void LsetHiddenApiExemptions() throws NoSuchMethodException, NoSuchFieldException {
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        ApplicationInfo.class.getDeclaredField("longVersionCode");
        ApplicationInfo.class.getDeclaredField("HIDDEN_API_ENFORCEMENT_DEFAULT");
    }

    @Test
    public void MclearHiddenApiExemptions() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        exception.expectMessage(containsString("setHiddenApiExemptions"));
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("L"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.clearHiddenApiExemptions());
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void NaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Ldalvik/system/VMRuntime;"));
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void OtestCheckArgsForInvokeMethod() {
        class X {
        }
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{}, new Object[]{new Object()}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1.0}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{null}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{1}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{null}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Object.class}, new Object[]{new X()}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{X.class}, new Object[]{new Object()}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Object.class, int.class, byte.class, short.class, char.class, double.class, float.class, boolean.class, long.class}, new Object[]{new X(), 1, (byte) 0, (short) 2, 'c', 1.1, 1.2f, false, 114514L}));
    }

    private static boolean containsField(List<Field> fields, String name) {
        for (var field : fields) {
            if (field.getName().equals(name)) return true;
        }
        return false;
    }

    private static void assertHiddenApiPolicyPermissive() throws NoSuchFieldException {
        ApplicationInfo.class.getDeclaredField("longVersionCode");
    }

    private static File resolveHiddenApiFile(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(InstrumentationRegistry.getInstrumentation().getContext().getFilesDir(), path);
    }

    private static int writePresentHiddenApiFields(String className,
                                                   Map<String, String> expectedFields,
                                                   BufferedWriter writer) throws IOException {
        if (isKnownUnsupportedFieldClass(className)) {
            return -1;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, null);
        } catch (ClassNotFoundException | LinkageError e) {
            return -1;
        }

        Set<String> foundFields = new HashSet<>();
        for (var field : clazz.getDeclaredFields()) {
            foundFields.add(field.getName());
        }

        int presentFields = 0;
        for (var field : expectedFields.entrySet()) {
            if (!foundFields.contains(field.getKey())) continue;
            writer.write(field.getValue());
            writer.newLine();
            presentFields++;
        }
        return presentFields;
    }

    private static int checkHiddenApiFields(String className, Set<String> expectedFields,
                                            StringBuilder missing) {
        if (isKnownUnsupportedFieldClass(className)) {
            return -1;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, null);
        } catch (ClassNotFoundException | LinkageError e) {
            return -1;
        }

        Set<String> foundFields = new HashSet<>();
        for (var field : HiddenApiBypass.getInstanceFields(clazz)) {
            foundFields.add(field.getName());
        }
        for (var field : HiddenApiBypass.getStaticFields(clazz)) {
            foundFields.add(field.getName());
        }

        int missingFields = 0;
        for (String field : expectedFields) {
            if (foundFields.contains(field)) continue;
            missingFields++;
            if (missing.length() < 4096) {
                missing.append(className).append('.').append(field).append('\n');
            }
        }
        return missingFields;
    }

    private static boolean isKnownUnsupportedFieldClass(String className) {
        return "java.lang.Object".equals(className)
                || "java.lang.ref.FinalizerReference".equals(className);
    }

    private static String[] parseHiddenApiField(String line) {
        if (line.contains(",public-api")) return null;
        int arrow = line.indexOf("->");
        if (arrow <= 1 || line.charAt(0) != 'L') return null;
        int colon = line.indexOf(':', arrow + 2);
        if (colon < 0) return null;
        int paren = line.indexOf('(', arrow + 2);
        if (paren >= 0 && paren < colon) return null;
        String descriptor = line.substring(0, arrow);
        if (!descriptor.endsWith(";")) return null;
        String name = line.substring(arrow + 2, colon);
        return new String[]{descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), name};
    }

}
