package expo.modules.appblockerengine.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import expo.modules.appblockerengine.blocker.service.BlockerService
import expo.modules.appblockerengine.blocker.storage.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // An update kills the running service like a reboot does; both may start a
        // foreground service from the background.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        
        val preferencesManager = PreferencesManager.getInstance(context)
        val state = preferencesManager.loadState()
        
        if (state.isBlocking || state.scheduledTime != null) {
            BlockerService.startService(context)
        }
    }
}
