package ltd.nextalone.pkginstallerplus.hook

import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageInfo
import android.graphics.Typeface
import android.os.Build
import android.os.UserManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ltd.nextalone.pkginstallerplus.R
import ltd.nextalone.pkginstallerplus.utils.*

private const val TAG_INSTALL_DETAILS = "IPP_install_details"
private const val TAG_UNINSTALL_DETAILS = "IPP_uninstall_details"
private const val INSTALL_ACTION_STAGE = "InstallUserActionRequired"
private const val UNINSTALL_ACTION_STAGE = "UninstallUserActionRequired"

/** Android 16 / PackageInstaller v2 hook. */
object InstallerHookBaklava {
    fun initOnce() {
        val installFragmentClass =
            "$INSTALLER_V2_PKG.fragments.InstallationFragment".clazz
                ?: error("PackageInstaller v2 InstallationFragment not found")
        val installUpdate = installFragmentClass.method("updateUI", 0, Void.TYPE)
            ?: error("InstallationFragment.updateUI() not found")

        installUpdate.hookAfter {
            val fragment = it.thisObject
            val dialog = fragment.get("mDialog") as? Dialog ?: return@hookAfter
            val activity = fragment.requireActivityOrNull() ?: return@hookAfter
            val stage = currentInstallStage(fragment, activity)

            if (stage?.javaClass?.simpleName == INSTALL_ACTION_STAGE) {
                addInstallDetails(fragment, activity, dialog)
            } else {
                removeInstallDetails(fragment, dialog)
            }
        }

        val uninstallFragmentClass =
            "$INSTALLER_V2_PKG.fragments.UninstallationFragment".clazz ?: return
        val uninstallUpdate = uninstallFragmentClass.method("updateUI", 0, Void.TYPE) ?: return

        uninstallUpdate.hookAfter {
            val fragment = it.thisObject
            val dialog = fragment.get("mDialog") as? Dialog ?: return@hookAfter
            val activity = fragment.requireActivityOrNull() ?: return@hookAfter
            val stage = currentUninstallStage(fragment, activity)

            if (stage?.javaClass?.simpleName == UNINSTALL_ACTION_STAGE) {
                addUninstallDetails(fragment, activity, dialog)
            } else {
                removeUninstallDetails(fragment, dialog)
            }
        }
    }

    private fun Any.requireActivityOrNull(): Activity? =
        runCatching { javaClass.getMethod("requireActivity").invoke(this) as? Activity }.getOrNull()

    private fun unwrapLiveData(candidate: Any?): Any? {
        if (candidate == null) return null
        return candidate.callNoArg("getValue") ?: candidate.get("mData") ?: candidate
    }

    private fun currentInstallStage(fragment: Any, activity: Activity): Any? {
        fragment.callNoArg("getCurrentInstallStage")?.let { return it }
        val viewModel = activity.get("installViewModel") ?: return null
        viewModel.callNoArg("getCurrentInstallStage")?.let { return unwrapLiveData(it) }
        return unwrapLiveData(viewModel.getFirst("_currentInstallStage", "currentInstallStage"))
    }

    private fun currentUninstallStage(fragment: Any, activity: Activity): Any? {
        fragment.callNoArg("getCurrentUninstallStage")?.let { return it }
        val viewModel = activity.get("uninstallViewModel") ?: return null
        viewModel.callNoArg("getCurrentUninstallStage")?.let { return unwrapLiveData(it) }
        return unwrapLiveData(viewModel.getFirst("_currentUninstallStage", "currentUninstallStage"))
    }

    private fun appSnippet(fragment: Any, dialog: Dialog): View? =
        fragment.get("mAppSnippet") as? View ?: dialog.findHostView<View>("app_snippet")

    private fun installRepository(activity: Activity): Any? =
        activity.get("installRepository")
            ?: activity.get("installViewModel")?.get("repository")

    private fun uninstallRepository(activity: Activity): Any? =
        activity.get("uninstallRepository")
            ?: activity.get("uninstallViewModel")?.get("repository")

    private fun addInstallDetails(fragment: Any, activity: Activity, dialog: Dialog) {
        val snippet = appSnippet(fragment, dialog) ?: return
        val parent = snippet.parent as? ViewGroup ?: return
        val repository = installRepository(activity) ?: return
        val newPkgInfo = (repository.get("newPackageInfo")
            ?: repository.callNoArg("getNewPackageInfo")) as? PackageInfo ?: return
        val userManager = (repository.get("userManager")
            ?: repository.callNoArg("getUserManager")) as? UserManager
        val oldPkgInfo = activity.packageManager.getPackageInfoOrNull(newPkgInfo.packageName)

        val labelUser = activity.moduleString(R.string.IPP_info_user, "User")
        val labelPackage = activity.moduleString(R.string.IPP_info_package, "Package name")
        val labelVersion = activity.moduleString(R.string.IPP_info_version, "Version")
        val labelSdk = activity.moduleString(R.string.IPP_info_sdk, "Target SDK")

        val sb = SpannableStringBuilder()
        if (userManager != null) {
            val userName = runCatching { userManager.userName }.getOrNull()
            if (!userName.isNullOrEmpty()) sb.append("$labelUser: $userName\n")
        }
        sb.append("$labelPackage: ")
            .append(
                newPkgInfo.packageName,
                ForegroundColorSpan(ThemeUtil.colorGreen),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            ).append('\n')

        if (oldPkgInfo == null) {
            sb.append("$labelVersion: ")
                .append(
                    newPkgInfo.versionLabel(),
                    ForegroundColorSpan(ThemeUtil.colorGreen),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                ).append('\n')
                .append("$labelSdk: ")
                .append(
                    newPkgInfo.applicationInfo?.targetSdkVersion?.toString() ?: "N/A",
                    ForegroundColorSpan(ThemeUtil.colorGreen),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
        } else {
            sb.append("$labelVersion: ")
                .append(
                    oldPkgInfo.versionLabel(),
                    ForegroundColorSpan(ThemeUtil.colorRed),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                ).append(" ➞ ")
                .append(
                    newPkgInfo.versionLabel(),
                    ForegroundColorSpan(ThemeUtil.colorGreen),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                ).append('\n')
                .append("$labelSdk: ")
                .append(
                    oldPkgInfo.applicationInfo?.targetSdkVersion?.toString() ?: "N/A",
                    ForegroundColorSpan(ThemeUtil.colorRed),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                ).append(" ➞ ")
                .append(
                    newPkgInfo.applicationInfo?.targetSdkVersion?.toString() ?: "N/A",
                    ForegroundColorSpan(ThemeUtil.colorGreen),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
        }

        upsertDetails(parent, snippet, TAG_INSTALL_DETAILS, activity, sb)
    }

    private fun removeInstallDetails(fragment: Any, dialog: Dialog) {
        val snippet = appSnippet(fragment, dialog) ?: return
        val parent = snippet.parent as? ViewGroup ?: return
        parent.findViewWithTag<View>(TAG_INSTALL_DETAILS)?.let { parent.removeView(it) }
    }

    private fun addUninstallDetails(fragment: Any, activity: Activity, dialog: Dialog) {
        val snippet = appSnippet(fragment, dialog) ?: return
        val parent = snippet.parent as? ViewGroup ?: return

        val repository = uninstallRepository(activity) ?: return
        val packageName = (repository.get("targetPackageName")
            ?: repository.callNoArg("getTargetPackageName")) as? String ?: return
        val pkgInfo = activity.packageManager.getPackageInfoOrNull(packageName) ?: return

        val labelPackage = activity.moduleString(R.string.IPP_info_package, "Package name")
        val labelVersion = activity.moduleString(R.string.IPP_info_version, "Version")
        val labelSdk = activity.moduleString(R.string.IPP_info_sdk, "Target SDK")

        val sb = SpannableStringBuilder()
        sb.append("$labelPackage: ")
            .append(
                packageName,
                ForegroundColorSpan(ThemeUtil.colorRed),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            ).append('\n')
            .append("$labelVersion: ")
            .append(
                pkgInfo.versionLabel(),
                ForegroundColorSpan(ThemeUtil.colorRed),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            ).append('\n')
            .append("$labelSdk: ")
            .append(
                pkgInfo.applicationInfo?.targetSdkVersion?.toString() ?: "N/A",
                ForegroundColorSpan(ThemeUtil.colorRed),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

        upsertDetails(parent, snippet, TAG_UNINSTALL_DETAILS, activity, sb)
    }

    private fun removeUninstallDetails(fragment: Any, dialog: Dialog) {
        val snippet = appSnippet(fragment, dialog) ?: return
        val parent = snippet.parent as? ViewGroup ?: return
        parent.findViewWithTag<View>(TAG_UNINSTALL_DETAILS)?.let { parent.removeView(it) }
    }

    private fun upsertDetails(
        parent: ViewGroup,
        anchor: View,
        tagName: String,
        activity: Activity,
        content: CharSequence,
    ) {
        val existing = parent.findViewWithTag<TextView>(tagName)
        if (existing != null) {
            existing.text = content
            return
        }

        val view = TextView(activity).apply {
            tag = tagName
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setPadding(0, activity.dip2px(8f), 0, 0)
            text = content
        }
        val index = parent.indexOfChild(anchor)
        if (index >= 0 && index + 1 <= parent.childCount) {
            parent.addView(view, index + 1)
        } else {
            parent.addView(view)
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.compatLongVersionCode(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

private fun PackageInfo.versionLabel(): String =
    "${versionName ?: "N/A"}(${compatLongVersionCode()})"
