package app.drawerclear

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** The only app Drawer Clear is allowed to touch. */
const val LAUNCHER = "com.nothing.launcher"

/**
 * Runs shell commands with adb-level permissions through Shizuku.
 *
 * Every command is checked against [ALLOWED] first: only overlay commands on
 * the Nothing Launcher and restarting it. Anything else throws and never runs.
 */
object ShizukuShell {
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    data class Status(val state: State, val uid: String)

    data class Result(val code: Int, val output: String) {
        fun summary() = if (output.isBlank()) "exit $code" else "$output\nexit $code"
    }

    private val L = Regex.escape(LAUNCHER)
    private val ALLOWED = listOf(
        Regex("""cmd overlay list $L"""),
        Regex("""cmd overlay fabricate --target $L --name dc_\w+ $L:color/\w+ 0x1c 0x[0-9a-f]{8}( night)?"""),
        Regex("""cmd overlay (enable|disable) [\w.]+:dc_\w+"""),
        Regex("""am force-stop $L"""),
    )

    class BlockedCommand(command: String) : SecurityException("Blocked: \"$command\" is not an allowed command")

    fun status(): Status = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> Status(State.NOT_RUNNING, "")
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                Status(State.NO_PERMISSION, uidLabel())
            else -> Status(State.READY, uidLabel())
        }
    } catch (e: Throwable) {
        Status(State.NOT_RUNNING, "")
    }

    private fun uidLabel() = when (val uid = Shizuku.getUid()) {
        0 -> "root"
        2000 -> "shell (adb)"
        else -> "uid $uid"
    }

    // Shizuku made newProcess private in favour of UserService, but for
    // one-shot shell commands it is still the simplest route.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    fun run(command: String): Result {
        if (ALLOWED.none { it.matches(command) }) throw BlockedCommand(command)
        // No `sh -c`: the command is split into plain arguments, so nothing
        // in it can be interpreted as a second command.
        val argv = command.split(' ').toTypedArray()
        val process = newProcess.invoke(null, argv, null, null) as Process
        val output = process.inputStream.bufferedReader().use { it.readText() } +
            process.errorStream.bufferedReader().use { it.readText() }
        return Result(process.waitFor(), output.trim())
    }
}
