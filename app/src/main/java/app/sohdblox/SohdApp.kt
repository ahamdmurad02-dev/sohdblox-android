package app.sohdblox

import android.app.Application

class SohdApp : Application() {
    lateinit var api: app.sohdblox.net.SohdApi
        private set

    override fun onCreate() {
        super.onCreate()
        api = app.sohdblox.net.SohdApi(this)
    }
}
