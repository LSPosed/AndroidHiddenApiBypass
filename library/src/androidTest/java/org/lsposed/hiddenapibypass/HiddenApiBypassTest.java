package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("JavaReflectionMemberAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4.class)
public class HiddenApiBypassTest {

    private final Class<?> runtime = Class.forName("dalvik.system.VMRuntime");

    @SuppressWarnings("unused")
    private static class ManyFields {
        int i00;
        int i01;
        int i02;
        int i03;
        int i04;
        int i05;
        int i06;
        int i07;
        int i08;
        int i09;
        int i10;
        int i11;
        int i12;
        int i13;
        int i14;
        int i15;
        int i16;
        int i17;
        int i18;
        int i19;
        int i20;
        int i21;
        int i22;
        int i23;
        int i24;
        int i25;
        int i26;
        int i27;
        int i28;
        int i29;
        int i30;
        int i31;
        int i32;
        int i33;
        int i34;
        long i35;
        static int s00;
        static int s01;
        static int s02;
        static int s03;
        static int s04;
        static int s05;
        static int s06;
        static int s07;
        static int s08;
        static int s09;
        static int s10;
        static int s11;
        static int s12;
        static int s13;
        static int s14;
        static int s15;
        static int s16;
        static int s17;
        static int s18;
        static int s19;
        static int s20;
        static int s21;
        static int s22;
        static int s23;
        static int s24;
        static int s25;
        static int s26;
        static int s27;
        static int s28;
        static int s29;
        static int s30;
        static int s31;
        static int s32;
        static int s33;
        static int s34;
        static long s35;
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public HiddenApiBypassTest() throws ClassNotFoundException {
    }

    @BeforeClass
    public static void setUp() {
        var context = InstrumentationRegistry.getInstrumentation().getContext();
        Helper.enableOffsetCache(context);
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
        var field = HiddenApiBypass.getInstanceFields(ApplicationInfo.class).stream()
                .filter(i -> i.getName().equals("longVersionCode"))
                .findFirst();
        assertTrue(field.isPresent());
        assertSame(long.class, field.get().getType());
    }

    @Test
    public void HtestGetStaticFields() {
        var field = HiddenApiBypass.getStaticFields(ApplicationInfo.class).stream()
                .filter(i -> i.getName().equals("HIDDEN_API_ENFORCEMENT_DEFAULT"))
                .findFirst();
        assertTrue(field.isPresent());
        assertSame(int.class, field.get().getType());
    }

    @Test
    public void ItestFieldCloneManyFields() throws IllegalAccessException {
        var instanceField = HiddenApiBypass.getInstanceFields(ManyFields.class).stream()
                .filter(i -> i.getName().equals("i35"))
                .findFirst();
        assertTrue(instanceField.isPresent());
        assertSame(long.class, instanceField.get().getType());
        var manyFields = new ManyFields();
        instanceField.get().setAccessible(true);
        instanceField.get().setLong(manyFields, 123456789L);
        assertEquals(123456789L, manyFields.i35);

        var staticField = HiddenApiBypass.getStaticFields(ManyFields.class).stream()
                .filter(i -> i.getName().equals("s35"))
                .findFirst();
        assertTrue(staticField.isPresent());
        assertSame(long.class, staticField.get().getType());
        staticField.get().setAccessible(true);
        staticField.get().setLong(null, 987654321L);
        assertEquals(987654321L, ManyFields.s35);
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

    @Test
    public void PtestCachedOffset() {
        var context = InstrumentationRegistry.getInstrumentation().getContext();
        var artVersion = Helper.getArtVersion(context);
        var isNew = artVersion >= 36_00_00000L;
        var data = new long[6];
        data[0] = 24;
        data[1] = 12;
        data[2] = 24;
        data[3] = 48;
        data[4] = 40;
        data[5] = isNew ? 40 : 56;
        assertArrayEquals("art version " + artVersion, data, Helper.getCachedOffsetData());
    }

}
