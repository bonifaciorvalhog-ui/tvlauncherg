package com.example.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import com.example.tvlauncher.R
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.HomeRow
import com.example.tvlauncher.model.RowStyle

class AppRepository(private val context: Context) {

    fun loadAppsRow(): HomeRow =
        HomeRow("apps", context.getString(R.string.apps_row), RowStyle.APPS, loadApps())

    fun loadApps(): List<HomeItem> {
        val items = safely { fromLauncherIntents() }
            .ifEmpty { safely { fromInstalledPackages() } }
        return items.sortedBy { it.title.lowercase() }
    }

    private inline fun safely(block: () -> List<HomeItem>): List<HomeItem> =
        try {
            block()
        } catch (e: Exception) {
            emptyList()
        }

    @Suppress("DEPRECATION")
    private fun fromLauncherIntents(): List<HomeItem> {
        val pm = context.packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.toItem() }
    }

    @Suppress("DEPRECATION")
    private fun fromInstalledPackages(): List<HomeItem> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                val launch = pm.getLaunchIntentForPackage(info.packageName) ?: return@mapNotNull null
                HomeItem(info.packageName, info.loadLabel(pm).toString(), intent = launch).also {
                    it.drawable = info.loadBanner(pm) ?: info.loadIcon(pm)
                }
            }
    }

    private fun ResolveInfo.toItem(): HomeItem {
        val pm = context.packageManager
        val pkg = activityInfo.packageName
        val launch = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(pkg, activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return HomeItem(id = pkg, title = loadLabel(pm).toString(), intent = launch).also {
            it.drawable = activityInfo.loadBanner(pm) ?: loadIcon(pm)
        }
    }
}
