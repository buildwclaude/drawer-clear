package app.drawerclear

/**
 * Fabricated runtime resource overlays (Android 12+): replace one colour
 * resource inside the launcher without root or a signed overlay APK.
 */
object Overlay {
    private const val PREFIX = "dc_"
    private val listLine = Regex("""^\s*\[(.)\]\s+(\S+)""")

    data class Ours(val id: String, val enabled: Boolean)

    private fun overlayName(resName: String) =
        PREFIX + resName.substringAfter('/').replace(Regex("[^A-Za-z0-9_]"), "_")

    /** Our overlays on [pkg], parsed from `cmd overlay list`. */
    fun list(pkg: String): List<Ours> =
        ShizukuShell.run("cmd overlay list $pkg").output.lines().mapNotNull { line ->
            val m = listLine.find(line) ?: return@mapNotNull null
            val id = m.groupValues[2]
            if (":$PREFIX" in id) Ours(id, m.groupValues[1] == "x") else null
        }

    /** [resName] is fully qualified, e.g. `com.nothing.launcher:color/foo`. */
    fun apply(pkg: String, resName: String, argb: Int): String = buildString {
        val name = overlayName(resName)
        // 0x1c = TypedValue.TYPE_INT_COLOR_ARGB8
        val fabricate = "cmd overlay fabricate --target $pkg --name $name $resName 0x1c 0x%08x".format(argb)
        appendLine("$ $fabricate")
        appendLine(ShizukuShell.run(fabricate).summary())

        val id = list(pkg).firstOrNull { it.id.endsWith(":$name") }?.id
        if (id == null) {
            appendLine("Overlay didn't appear in 'cmd overlay list', so fabricate failed.")
            return@buildString
        }
        val enable = "cmd overlay enable $id"
        appendLine("$ $enable")
        appendLine(ShizukuShell.run(enable).summary())
        append("Open the app drawer. If nothing changed, tap Restart launcher.")
    }

    fun disableAll(pkg: String): String = buildString {
        val ours = list(pkg)
        if (ours.isEmpty()) append("No Drawer Clear overlays found on $pkg.")
        for (o in ours) {
            val cmd = "cmd overlay disable ${o.id}"
            appendLine("$ $cmd")
            appendLine(ShizukuShell.run(cmd).summary())
        }
    }

    fun restartLauncher(pkg: String): String {
        val cmd = "am force-stop $pkg"
        return "$ $cmd\n" + ShizukuShell.run(cmd).summary()
    }
}
