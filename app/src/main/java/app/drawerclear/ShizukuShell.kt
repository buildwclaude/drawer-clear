package app.drawerclear

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands with adb-level permissions through Shizuku. */
object ShizukuShell {
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    data class Status(val state: State, val uid: String)

    data class Result(val code: Int, val output: String) {
        fun summary() = if (output.isBlank()) "exit $code" else "$output\nexit $code"
    }

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
        val process = newProcess.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return Result(process.waitFor(), output.trim())
    }
}
