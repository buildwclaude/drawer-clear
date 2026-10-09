package app.drawerclear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val shizuku = mutableStateOf(ShizukuShell.Status(ShizukuShell.State.NOT_RUNNING, ""))
    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private fun refresh() {
        shizuku.value = ShizukuShell.status()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPermission)
        setContent {
            AppTheme {
                DrawerClearScreen(
                    shizuku = shizuku.value,
                    onGrant = { runCatching { Shizuku.requestPermission(1) } },
                    onRefresh = ::refresh,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPermission)
        super.onDestroy()
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}
