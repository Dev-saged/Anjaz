package com.anjaz.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.background
import com.anjaz.app.domain.Profile
import com.anjaz.app.ui.login.AppSessionViewModel
import com.anjaz.app.ui.login.LoginScreen
import com.anjaz.app.ui.login.SessionState
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.components.GlassNavBar
import com.anjaz.app.ui.components.NavItem
import com.anjaz.app.ui.home.HomeScreen
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.more.ChatPlaceholder
import com.anjaz.app.ui.more.MoreScreen
import com.anjaz.app.ui.tasks.NewTaskSheet
import com.anjaz.app.ui.tasks.TasksScreen
import com.anjaz.app.ui.tasks.TasksViewModel
import com.anjaz.app.ui.test.SelfTestScreen

object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val CHAT = "chat"
    const val MORE = "more"
    const val TEST = "test"
}

@Composable
fun AnjazRoot(session: AppSessionViewModel = hiltViewModel()) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val st = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(Anjaz.colors.surface))
        SessionState.LoggedOut -> LoginScreen()
        is SessionState.LoggedIn -> MainShell(st.profile, onSignOut = { session.signOut() })
    }
}

@Composable
private fun MainShell(profile: Profile, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val tasksVm: TasksViewModel = hiltViewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val haptic = LocalHapticFeedback.current
    val sync: SyncViewModel = hiltViewModel()
    LaunchedEffect(profile.uid) { sync.start(profile) }
    val snack = remember { SnackbarHostState() }
    val message by tasksVm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); tasksVm.consumeMessage() } }

    val items = remember {
        listOf(
            NavItem(Routes.HOME, "الرئيسية", AnjazIcons.Home), NavItem(Routes.TASKS, "المهام", AnjazIcons.Tasks),
            NavItem(Routes.CHAT, "المحادثات", AnjazIcons.Chat), NavItem(Routes.MORE, "المزيد", AnjazIcons.More),
        )
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 40 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.HOME) { HomeScreen(onOpenTask = { nav.navigateTab(Routes.TASKS) }, onNewTask = tasksVm::openForm) }
            composable(Routes.TASKS) { TasksScreen(tasksVm, onNewTask = tasksVm::openForm) }
            composable(Routes.CHAT) { ChatPlaceholder() }
            composable(Routes.MORE) { MoreScreen(profile, onSignOut = onSignOut, onOpenSelfTest = { nav.navigate(Routes.TEST) }) }
            composable(Routes.TEST) { SelfTestScreen() }
        }
        if (route != Routes.TEST) {
            GlassNavBar(
                items, route, { nav.navigateTab(it) },
                onFab = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); tasksVm.openForm() },
                fabIcon = AnjazIcons.Plus, fabLabel = "مهمة جديدة", modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(snack, Modifier.align(Alignment.TopCenter))
        NewTaskSheet(tasksVm)
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
