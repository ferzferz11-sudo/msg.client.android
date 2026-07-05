package lavender.client.android

import android.app.Application
import lavender.client.android.data.fcm.ScreenStateReceiver

class LavenderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenStateReceiver.register(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ScreenStateReceiver.unregister(this)
    }
}
