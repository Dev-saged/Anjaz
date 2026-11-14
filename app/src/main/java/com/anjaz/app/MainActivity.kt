package com.anjaz.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.anjaz.app.push.DeepLink
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anjaz.app.ui.nav.AnjazRoot
import com.anjaz.app.ui.theme.AnjazTheme
import androidx.compose.foundation.layout.fillMaxSize
import com.anjaz.app.update.UpdateLayer
import com.anjaz.app.update.UpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var updates: UpdateManager

    companion object {
        const val ACTION_OPEN_UPDATE = "com.anjaz.app.OPEN_UPDATE"
        const val ACTION_INSTALL_UPDATE = "com.anjaz.app.INSTALL_UPDATE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeepLink.pending.value = intent?.getStringExtra("route")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AnjazTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { AnjazRoot(); UpdateLayer() } } }
        handleUpdateIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { updates.check() }
    }

    private fun handleUpdateIntent(i: Intent?) {
        val action = i?.action ?: return
        if (action != ACTION_OPEN_UPDATE && action != ACTION_INSTALL_UPDATE) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { updates.restore() }
            if (action == ACTION_INSTALL_UPDATE) updates.install(this@MainActivity) else updates.showSheet(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.pending.value = intent.getStringExtra("route")
        handleUpdateIntent(intent)
    }
}
