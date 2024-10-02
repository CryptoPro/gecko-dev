package mozilla.components.feature.cades.plugin

import android.annotation.SuppressLint
import android.content.Context
import mozilla.components.feature.cades.plugin.sdk.wrapper.JniInit
import mozilla.components.feature.cades.plugin.sdk.wrapper.RutokenInit
import kotlin.concurrent.thread

class CAdESPlugin private constructor(context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CAdESPlugin? = null
        fun init(context: Context): CAdESPlugin {
            var localRef = instance
            if (localRef == null) {
                synchronized(this) {
                    localRef = instance
                    if (localRef == null) {
                        localRef = CAdESPlugin(context)
                    }
                    instance = localRef
                }
            }
            return localRef!!
        }

    }
    init {
        // https://dev.rutoken.ru/pages/viewpage.action?pageId=54395671, но пока не нашел другого места.
        RutokenInit.init(context)
        // Инициализация провайдера должна быть первой!
        JniInit.initNativeCSP(context)
        JniInit.installLicenses()
        thread {
            // Запуск в вечном потоке цикла только после инициализации провайдера!
            JniInit.initMainCircle(context)
        }
    }

}