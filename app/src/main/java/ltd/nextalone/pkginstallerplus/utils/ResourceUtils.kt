package ltd.nextalone.pkginstallerplus.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View

internal const val MODULE_PACKAGE_NAME = "ltd.nextalone.pkginstallerplus"

internal fun Context.hostId(name: String): Int {
    val own = resources.getIdentifier(name, "id", packageName)
    if (own != 0) return own
    return resources.getIdentifier(name, "id", "com.android.packageinstaller")
}

internal fun Context.hostString(name: String): String? {
    val id = resources.getIdentifier(name, "string", packageName).takeIf { it != 0 }
        ?: resources.getIdentifier(name, "string", "com.android.packageinstaller").takeIf { it != 0 }
        ?: return null
    return runCatching { getString(id) }.getOrNull()
}

/**
 * Read InstallerPlus' own resources through a package context instead of adding
 * the module APK to the PackageInstaller AssetManager. This avoids the hidden
 * AssetManager.addAssetPath reflection path on Android 16.
 */
internal fun Context.moduleString(resId: Int, fallback: String): String {
    return runCatching {
        createPackageContext(MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY).getString(resId)
    }.getOrDefault(fallback)
}

internal fun <T : View?> Any.findHostView(name: String): T? {
    val id = when (this) {
        is View -> context.hostId(name)
        is Activity -> hostId(name)
        is Dialog -> context.hostId(name)
        else -> 0
    }
    if (id == 0) return null
    return when (this) {
        is View -> findViewById(id)
        is Activity -> findViewById(id)
        is Dialog -> findViewById(id)
        else -> null
    }
}
