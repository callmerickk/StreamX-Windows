package com.example.streamnest

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Launcher icon choices. Each one is an <activity-alias> in AndroidManifest.xml. */
data class AppIconOpt(val id: String, val alias: String, val label: String, val res: Int)

object AppIcons {
    val all = listOf(
        AppIconOpt("red", "IconRedGlow", "Red Glow", R.drawable.logo_mark),
        AppIconOpt("ribbon", "IconRibbonX", "Ribbon X", R.drawable.logo_ribbon),
        AppIconOpt("halo", "IconHaloPlay", "Halo Play", R.drawable.logo_halo),
    )
    private const val PREFS = "app_icon"

    private fun cn(ctx: Context, o: AppIconOpt) = ComponentName(ctx.packageName, "com.example.streamnest.${o.alias}") // the class namespace, not the package (TV app = ...streamnest.tv)

    /** The icon the launcher shows right now. */
    fun active(ctx: Context): String {
        val pm = ctx.packageManager
        return all.firstOrNull { o ->
            when (runCatching { pm.getComponentEnabledSetting(cn(ctx, o)) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> o.id == "red" // manifest default
                else -> false
            }
        }?.id ?: "red"
    }

    /** Icon chosen with "Later", waiting for the next fresh start (null = nothing pending). */
    fun pending(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString("pending", null)?.takeIf { it != active(ctx) }

    fun setPending(ctx: Context, id: String?) {
        ctx.getSharedPreferences(PREFS, 0).edit().apply { if (id == null) remove("pending") else putString("pending", id) }.apply()
    }

    /** Switch the launcher alias. Enables the new one first so there is always a launcher entry. */
    private fun switchTo(ctx: Context, id: String) {
        val pm = ctx.packageManager
        val target = all.firstOrNull { it.id == id } ?: return
        runCatching {
            pm.setComponentEnabledSetting(cn(ctx, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            all.filter { it.id != id }.forEach {
                pm.setComponentEnabledSetting(cn(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** Called on a fresh app start: applies an icon chosen with "Later". */
    fun applyPendingOnColdStart(ctx: Context) {
        val p = pending(ctx) ?: return
        switchTo(ctx, p)
        setPending(ctx, null)
    }

    /** "Restart now": switch the icon and relaunch StreamX through the new icon. */
    fun applyAndRestart(activity: Activity, id: String) {
        setPending(activity, null)
        switchTo(activity, id)
        val target = all.first { it.id == id }
        val intent = Intent.makeRestartActivityTask(cn(activity, target))
        activity.startActivity(intent)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
