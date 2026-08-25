package ltd.nextalone.pkginstallerplus.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import ltd.nextalone.pkginstallerplus.HookEntry
import java.lang.reflect.Member
import java.lang.reflect.Method

const val INSTALLER_V2_PKG = "com.android.packageinstaller.v2.ui"

internal val String.clazz: Class<*>?
    get() = try {
        HookEntry.lpClassLoader.loadClass(this)
    } catch (t: Throwable) {
        null
    }

internal fun Member.hook(callback: XC_MethodHook) = try {
    XposedBridge.hookMethod(this, callback)
} catch (e: Throwable) {
    Log.e(TAG, e.message, e)
    null
}

internal inline fun Member.hookBefore(crossinline hooker: (XC_MethodHook.MethodHookParam) -> Unit) =
    hook(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(TAG, e.message, e)
            }
        }
    })

internal inline fun Member.hookAfter(crossinline hooker: (XC_MethodHook.MethodHookParam) -> Unit) =
    hook(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                hooker(param)
            } catch (e: Throwable) {
                Log.e(TAG, e.message, e)
            }
        }
    })

internal fun Member.replace(result: Any?) = this.replace { result }

internal inline fun <T : Any> Member.replace(crossinline hooker: (XC_MethodHook.MethodHookParam) -> T?) =
    hook(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? = hooker(param)
    })

internal fun Class<*>.method(name: String): Method? {
    return declaredMethods.firstOrNull { it.name == name }?.apply {
        runCatching { isAccessible = true }
    }
}

internal fun Class<*>.method(name: String, vararg argsTypesAndReturnType: Any): Method? {
    val argc = argsTypesAndReturnType.size / 2
    val argTypes = Array<Class<*>?>(argc) { null }
    var returnType: Class<*>? = null
    if (argc * 2 + 1 == argsTypesAndReturnType.size) {
        returnType = argsTypesAndReturnType.last() as Class<*>
    }
    for (i in 0 until argc) argTypes[i] = argsTypesAndReturnType[argc + i] as Class<*>

    var clz: Class<*>? = this
    while (clz != null) {
        val found = clz.declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.parameterTypes.contentEquals(argTypes) &&
                (returnType == null || returnType == method.returnType)
        }
        if (found != null) {
            runCatching { found.isAccessible = true }
            return found
        }
        clz = clz.superclass
    }
    return null
}

internal fun Class<*>.method(
    size: Int,
    returnType: Class<*>,
    condition: (method: Member) -> Boolean = { true },
): Method? = declaredMethods.firstOrNull {
    it.returnType == returnType && it.parameterTypes.size == size && condition(it)
}?.apply { runCatching { isAccessible = true } }

internal fun Class<*>.method(
    name: String,
    size: Int,
    returnType: Class<*>,
    condition: (method: Member) -> Boolean = { true },
): Method? = declaredMethods.firstOrNull {
    it.name == name && it.returnType == returnType && it.parameterTypes.size == size && condition(it)
}?.apply { runCatching { isAccessible = true } }

internal val isV2InstallerAvailable: Boolean
    get() = "$INSTALLER_V2_PKG.InstallLaunch".clazz != null &&
        "$INSTALLER_V2_PKG.fragments.InstallationFragment".clazz != null

internal fun PackageManager.getPackageInfoOrNull(pkgName: String): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(pkgName, 0)
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}
