package app.drawerclear

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import java.io.File
import java.io.OutputStream

data class ResEntry(val id: Int, val type: String, val name: String)

/**
 * A colour resource or colour theme attr, how it resolves (the chain of
 * references), and which of the launcher's own colour resources that chain
 * passes through: those are what a fabricated overlay can replace.
 */
data class Resolved(
    val entry: ResEntry,
    val chain: String,
    val color: Int?,
    val targets: List<String>,
)

data class StyleRef(val id: Int, val name: String)

data class Scan(
    val pkg: String,
    val version: String,
    val theme: StyleRef?,
    val themes: List<StyleRef>,
    val typeCounts: Map<String, Int>,
    val attrs: List<Resolved>,
    val colors: List<Resolved>,
) {
    val candidates: List<Resolved> = (attrs + colors).filter { DRAWER_HINT.containsMatchIn(it.entry.name) }

    companion object {
        val DRAWER_HINT = Regex("(?i)all_?apps|scrim|drawer")
    }
}

object LauncherInspector {
    private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun detectHomePackage(ctx: Context): String? {
        val ri = ctx.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        // "android" means no default is set and the chooser would show.
        return ri.activityInfo?.packageName?.takeIf { it != "android" }
    }

    private fun homeActivity(ctx: Context, pkg: String): ActivityInfo? =
        ctx.packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .firstOrNull { it.packageName == pkg }

    fun scan(ctx: Context, pkg: String, themeOverride: Int? = null): Scan {
        val info = ctx.packageManager.getPackageInfo(pkg, 0)
        val res = ctx.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY).resources
        val entries = enumerate(res)

        val themeId = themeOverride
            ?: homeActivity(ctx, pkg)?.themeResource?.takeIf { it != 0 }
            ?: info.applicationInfo?.theme
            ?: 0
        val theme = res.newTheme().apply { if (themeId != 0) applyStyle(themeId, true) }

        val attrs = entries.filter { it.type == "attr" }.mapNotNull { e ->
            val tv = TypedValue()
            if (!theme.resolveAttribute(e.id, tv, false)) return@mapNotNull null
            resolve(res, theme, pkg, e, tv, emptyList()).takeIf { it.color != null }
        }
        val colors = entries.filter { it.type == "color" }.map { e ->
            val tv = TypedValue()
            res.getValue(e.id, tv, false)
            resolve(res, theme, pkg, e, tv, listOf("$pkg:color/${e.name}"))
        }

        return Scan(
            pkg = pkg,
            version = "${info.versionName} (${info.longVersionCode})",
            theme = if (themeId != 0) StyleRef(themeId, nameOf(res, themeId).substringAfter('/')) else null,
            themes = entries.filter { it.type == "style" && it.name.contains("Theme", ignoreCase = true) }
                .map { StyleRef(it.id, it.name) },
            typeCounts = entries.groupingBy { it.type }.eachCount(),
            attrs = attrs,
            colors = colors,
        )
    }

    /**
     * Resources has no "list everything" API, but app resource IDs are dense:
     * 0x7f TT EEEE. Walk each type until entries stop resolving.
     */
    private fun enumerate(res: Resources): List<ResEntry> {
        val out = ArrayList<ResEntry>()
        var emptyTypes = 0
        for (t in 1..0xFF) {
            var type: String? = null
            var misses = 0
            var e = 0
            var found = 0
            while (misses < 32 && e <= 0xFFFF) {
                val id = 0x7f000000 or (t shl 16) or e
                try {
                    val name = res.getResourceEntryName(id)
                    val typeName = type ?: res.getResourceTypeName(id).also { type = it }
                    out += ResEntry(id, typeName, name)
                    found++
                    misses = 0
                } catch (_: Resources.NotFoundException) {
                    misses++
                }
                e++
            }
            if (found == 0) {
                if (out.isNotEmpty() && ++emptyTypes >= 8) break
            } else {
                emptyTypes = 0
            }
        }
        return out
    }

    private fun resolve(
        res: Resources,
        theme: Resources.Theme,
        pkg: String,
        entry: ResEntry,
        tv: TypedValue,
        initialTargets: List<String>,
    ): Resolved {
        val parts = mutableListOf<String>()
        val targets = initialTargets.toMutableList()
        var color: Int? = null
        var lastRef = if (entry.type == "color") entry.id else 0

        run walk@{
            repeat(12) {
                when (tv.type) {
                    TypedValue.TYPE_ATTRIBUTE -> {
                        parts += "?" + short(nameOf(res, tv.data), pkg)
                        if (!theme.resolveAttribute(tv.data, tv, false)) {
                            parts += "(unset)"
                            return@walk
                        }
                    }
                    TypedValue.TYPE_REFERENCE -> {
                        val id = tv.data
                        if (id == 0) {
                            parts += "@null"
                            return@walk
                        }
                        val name = nameOf(res, id)
                        parts += "@" + short(name, pkg)
                        if (name.startsWith("$pkg:color/") && name !in targets) targets += name
                        lastRef = id
                        try {
                            res.getValue(id, tv, false)
                        } catch (_: Exception) {
                            parts += "(unreadable)"
                            return@walk
                        }
                    }
                    in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> {
                        color = tv.data
                        parts += hex(tv.data)
                        return@walk
                    }
                    TypedValue.TYPE_STRING -> {
                        // Colour state list XML; its default colour is what gets drawn.
                        color = runCatching { res.getColorStateList(lastRef, theme).defaultColor }.getOrNull()
                        parts += "selector" + (color?.let { " " + hex(it) } ?: "")
                        return@walk
                    }
                    else -> {
                        parts += "(type 0x${tv.type.toString(16)})"
                        return@walk
                    }
                }
            }
        }
        return Resolved(entry, parts.joinToString(" → "), color, targets)
    }

    private fun nameOf(res: Resources, id: Int): String =
        try {
            res.getResourceName(id)
        } catch (_: Exception) {
            "0x%08x".format(id)
        }

    private fun short(name: String, pkg: String) = name.removePrefix("$pkg:")

    fun hex(color: Int) = "#%08X".format(color)

    // ---- Sharing results ----

    private fun line(r: Resolved) = "${r.entry.type}/${r.entry.name} = ${r.chain}" +
        if (r.targets.isNotEmpty()) "   [targets: ${r.targets.joinToString { it.substringAfter('/') }}]" else ""

    private fun StringBuilder.header(s: Scan) {
        appendLine("Drawer Clear report")
        appendLine("Launcher: ${s.pkg} ${s.version}")
        appendLine("Theme: ${s.theme?.name ?: "none"}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}")
        appendLine("Resource types: " + s.typeCounts.entries.joinToString { "${it.key}=${it.value}" })
    }

    fun candidatesText(s: Scan): String = buildString {
        header(s)
        appendLine()
        appendLine("Likely drawer colours:")
        s.candidates.forEach { appendLine(line(it)) }
    }

    fun report(s: Scan): String = buildString {
        header(s)
        appendLine()
        appendLine("== Likely drawer colours (${s.candidates.size})")
        s.candidates.forEach { appendLine(line(it)) }
        appendLine()
        appendLine("== Colour attrs in theme (${s.attrs.size})")
        s.attrs.forEach { appendLine(line(it)) }
        appendLine()
        appendLine("== Colour resources (${s.colors.size})")
        s.colors.forEach { appendLine(line(it)) }
        appendLine()
        appendLine("== Themes (${s.themes.size})")
        s.themes.forEach { appendLine(it.name) }
    }

    fun saveReport(ctx: Context, s: Scan): String =
        saveToDownloads(ctx, "drawerclear-report-${s.pkg}.txt", "text/plain") {
            it.write(report(s).toByteArray())
        }

    /** Copies the launcher's APK file(s) to Downloads so it can be decompiled elsewhere. */
    fun exportApk(ctx: Context, pkg: String): List<String> {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        val paths = listOf(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        return paths.map { path ->
            val file = File(path)
            saveToDownloads(ctx, "$pkg-${file.name}", "application/vnd.android.package-archive") { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }
    }

    private fun saveToDownloads(ctx: Context, name: String, mime: String, write: (OutputStream) -> Unit): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DrawerClear")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create $name in Downloads")
        ctx.contentResolver.openOutputStream(uri)?.use(write) ?: error("Couldn't open $name")
        return "Download/DrawerClear/$name"
    }
}
