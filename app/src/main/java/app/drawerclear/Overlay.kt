package app.drawerclear

/**
 * Fabricated runtime resource overlays (Android 12+): replace one colour
 * resource inside the Nothing Launcher without root or a signed overlay APK.
 */
object Overlay {
    private const val PREFIX = "dc_"
    private val listLine = Regex("""^\s*\[(.)\]\s+(\S+)""")

    data class Ours(val id: String, val enabled: Boolean)

    private fun overlayName(resName: String) =
        PREFIX + resName.substringAfter('/').replace(Regex("[^A-Za-z0-9_]"), "_")

    /** Our overlays on the launcher, parsed from `cmd overlay list`. */
    fun list(): List<Ours> =
        ShizukuShell.run("cmd overlay list $LAUNCHER").output.lines().mapNotNull { line ->
            val m = listLine.find(line) ?: return@mapNotNull null
            val id = m.groupValues[2]
            if (":$PREFIX" in id) Ours(id, m.groupValues[1] == "x") else null
        }

    /**
     * [resName] must be one of the launcher's colours, e.g. `com.nothing.launcher:color/foo`.
     *
     * A fabricated overlay without a config only replaces the default value, so a
     * colour with a separate `-night` value would stay unchanged in dark mode.
     * We make two overlays: one for the default value and one for night.
     */
    fun apply(resName: String, argb: Int): String = buildString {
        require(resName.startsWith("$LAUNCHER:color/")) { "Only $LAUNCHER colours can be changed" }
        val base = overlayName(resName)
        for ((name, config) in listOf(base to "", "${base}_night" to " night")) {
            // 0x1c = TypedValue.TYPE_INT_COLOR_ARGB8
            val fabricate = "cmd overlay fabricate --target $LAUNCHER --name $name $resName 0x1c 0x%08x".format(argb) + config
            appendLine("$ $fabricate")
            appendLine(ShizukuShell.run(fabricate).summary())

            val id = list().firstOrNull { it.id.endsWith(":$name") }?.id
            if (id == null) {
                appendLine("Overlay $name didn't appear in 'cmd overlay list', so fabricate failed.")
                continue
            }
            val enable = "cmd overlay enable $id"
            appendLine("$ $enable")
            appendLine(ShizukuShell.run(enable).summary())
        }
        append("Open the app drawer. If nothing changed, tap Restart launcher.")
    }

    fun disableAll(): String = buildString {
        val ours = list()
        if (ours.isEmpty()) append("No Drawer Clear overlays found.")
        for (o in ours) {
            val cmd = "cmd overlay disable ${o.id}"
            appendLine("$ $cmd")
            appendLine(ShizukuShell.run(cmd).summary())
        }
    }

    fun restartLauncher(): String {
        val cmd = "am force-stop $LAUNCHER"
        return "$ $cmd\n" + ShizukuShell.run(cmd).summary()
    }
}
