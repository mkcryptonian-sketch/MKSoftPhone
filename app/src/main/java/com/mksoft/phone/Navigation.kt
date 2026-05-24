package com.mksoft.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mksoft.phone.data.DefaultDataRepository
import com.mksoft.phone.ui.main.LoginScreen
import com.mksoft.phone.ui.main.MainScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mksoft.phone.ui.main.MainScreenViewModel

@Composable
fun MainNavigation(viewModel: MainScreenViewModel = viewModel()) {
    val accounts by viewModel.savedAccounts.collectAsState(initial = null)
    val settings by viewModel.settings.collectAsState()

    if (accounts == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Show LoginScreen when: no saved accounts exist, OR user has explicitly logged out.
    if (accounts!!.isEmpty() || !settings.isLoggedIn) {
        LoginScreen(onLoginSuccess = { /* Navigation reacts to state */ }, viewModel = viewModel)
    } else {
        val backStack = rememberNavBackStack(Main)

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Main> {
                    MainScreen(
                        onItemClick = { navKey -> backStack.add(navKey) },
                        modifier = Modifier.safeDrawingPadding().padding(16.dp)
                    )
                }
            },
        )
    }
}

