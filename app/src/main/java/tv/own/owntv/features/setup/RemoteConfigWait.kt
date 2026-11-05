package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.hub.RemoteConfig
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * First run on a TV nobody is there to set up: a big code and "waiting". The playlist arrives from
 * afar ([RemoteConfig]) and the app enters the shell by itself; "Configurar manualmente" leaves for
 * the normal wizard.
 */
@Composable
internal fun RemoteConfigWait(onDone: (Long) -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val state by RemoteConfig.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { RemoteConfig.waitForSetup(context) }
    LaunchedEffect(state) { (state as? RemoteConfig.State.Done)?.let { onDone(it.profileId) } }
    val manualFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { manualFocus.requestFocus() } }

    val code = when (val s = state) {
        is RemoteConfig.State.Waiting -> s.code
        is RemoteConfig.State.Applying -> s.code
        is RemoteConfig.State.Failed -> s.code
        else -> RemoteConfig.savedCode(context)
    }
    val line = when (val s = state) {
        RemoteConfig.State.Connecting -> stringResource(R.string.dropz_remote_connecting)
        RemoteConfig.State.Offline -> stringResource(R.string.dropz_remote_offline)
        is RemoteConfig.State.Applying -> stringResource(R.string.dropz_remote_applying, s.name)
        is RemoteConfig.State.Failed -> stringResource(R.string.dropz_remote_failed, s.reason)
        else -> null
    }

    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.dropz_remote_wait_title),
        sub = stringResource(R.string.dropz_remote_wait_sub),
        back = null,
        next = WizardAction(stringResource(R.string.dropz_remote_manual), onManual, manualFocus),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.mpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            Text(stringResource(R.string.dropz_remote_code_label), style = stageText(22, 600), color = StageColors.Muted)
            Text(code ?: "····", style = stageText(120, 800), color = stageAccent.accent, textAlign = TextAlign.Center)
            if (line != null) {
                Text(line, style = stageText(20, 500), color = StageColors.Text, textAlign = TextAlign.Center)
            }
        }
    }
}
