package ltd.nextalone.pkginstallerplus;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookBaklava;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookN;
import ltd.nextalone.pkginstallerplus.hook.InstallerHookQ;

import static ltd.nextalone.pkginstallerplus.utils.HookUtilsKt.isV2InstallerAvailable;
import static ltd.nextalone.pkginstallerplus.utils.LogUtilsKt.logDebug;
import static ltd.nextalone.pkginstallerplus.utils.LogUtilsKt.logDetail;
import static ltd.nextalone.pkginstallerplus.utils.LogUtilsKt.logError;
import static ltd.nextalone.pkginstallerplus.utils.LogUtilsKt.logThrowable;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    public static ClassLoader myClassLoader;
    public static ClassLoader lpClassLoader;
    private static boolean sInitialized = false;
    private static String sModulePath = null;
    private static long sResInjectBeginTime = 0;
    private static long sResInjectEndTime = 0;

    private static void initializeHookInternal(LoadPackageParam lpparam) {
        logDebug("initializeHookInternal start: pkg=" + lpparam.packageName
                + ", sdk=" + VERSION.SDK_INT);
        lpClassLoader = lpparam.classLoader;

        // Android 16 / AOSP PackageInstaller v2. Do not also install the legacy
        // Q hook into the same process: the two UI implementations are unrelated.
        if (isV2InstallerAvailable()) {
            try {
                logDebug("initializeHook: PackageInstaller v2 / Android 16+");
                InstallerHookBaklava.INSTANCE.initOnce();
                return;
            } catch (Throwable t) {
                // If a ROM partially backports v2 but changes its internals, keep a
                // legacy fallback instead of leaving the installer completely unhooked.
                logThrowable("initializeHook(v2): ", t);
            }
        }

        try {
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                logDebug("initializeHook: legacy Q+");
                InstallerHookQ.INSTANCE.initOnce();
                return;
            }
            throw new IllegalStateException("Unsupported API " + VERSION.SDK_INT);
        } catch (Throwable e) {
            try {
                logDebug("initializeHook: legacy N");
                InstallerHookN.INSTANCE.initOnce();
            } catch (Throwable e1) {
                e.addSuppressed(e1);
                logThrowable("initializeHookInternal: ", e);
            }
        }
    }

    /**
     * Legacy resource injection used only by the old N/Q hooks.
     * Android 16 v2 deliberately does not depend on this hidden-API path.
     */
    public static void injectModuleResources(Resources res) {
        logDebug("injectModuleResources start");
        if (res == null) return;
        try {
            res.getString(R.string.IPP_res_inject_success);
            return;
        } catch (Resources.NotFoundException ignored) {
        }
        try {
            if (myClassLoader == null) myClassLoader = HookEntry.class.getClassLoader();
            if (sModulePath == null) throw new IllegalStateException("sModulePath is null");
            if (sResInjectBeginTime == 0) sResInjectBeginTime = System.currentTimeMillis();

            AssetManager assets = res.getAssets();
            @SuppressLint("DiscouragedPrivateApi")
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(assets, sModulePath);
            try {
                logDetail("injectModuleResources", res.getString(R.string.IPP_res_inject_success));
                if (sResInjectEndTime == 0) sResInjectEndTime = System.currentTimeMillis();
            } catch (Resources.NotFoundException e) {
                logError("injectModuleResources failed: cookie=" + cookie + ", path=" + sModulePath);
                try {
                    File f = new File(sModulePath);
                    logError("module path: exists=" + f.exists() + ", dir=" + f.isDirectory()
                            + ", readable=" + f.canRead() + ", size=" + f.length());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            logThrowable("injectModuleResources: ", e);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        logDetail("handleLoadPackage", lpparam.packageName);
        if ("com.google.android.packageinstaller".equals(lpparam.packageName)
                || "com.android.packageinstaller".equals(lpparam.packageName)
                || "com.android.permissioncontroller".equals(lpparam.packageName)) {
            if (!sInitialized) {
                sInitialized = true;
                initializeHookInternal(lpparam);
            }
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        if (startupParam == null || startupParam.modulePath == null) {
            throw new IllegalStateException("InstallerPlus modulePath is null");
        }
        sModulePath = startupParam.modulePath;
    }
}
