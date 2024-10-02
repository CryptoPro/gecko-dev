package mozilla.components.feature.cades.plugin

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.feature.cades.plugin.wrapper.JniWrapper
import mozilla.components.support.base.log.logger.Logger
import ru.CryptoPro.JCSP.NCSPConfig
import kotlin.concurrent.thread

class CAdESPlugin private constructor(val context: Context, val logger: Logger) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CAdESPlugin? = null
        suspend fun init(context: Context, logger: Logger): CAdESPlugin = withContext(Dispatchers.IO) {
            var localRef = instance
            if (localRef == null) {
                synchronized(this) {
                    localRef = instance
                    if (localRef == null) {
                        localRef = CAdESPlugin(context, logger)
                    }
                    instance = localRef
                }
            }
            return@withContext localRef!!
        }
        internal const val CAdES_OCSP_LICENSE = "0A202-U0030-00ECW-RRLMF-UU2WK"
        internal const val CAdES_TSP_LICENSE = "TA200-G0030-00ECW-RRLNE-BTDVV"
    }
    init {
        // Инициализация провайдера должна быть первой!
        initNativeCSP()
        installLicenses()
        thread {
            // Запуск в вечном потоке цикла только после инициализации провайдера!
            initMainCircle()
        }
    }
    private fun initNativeCSP() {
        logger.info("Initiating native CSP...")
        val error = NCSPConfig.init(context)
        if (error != NCSPConfig.CSP_INIT_OK) {
            logger.error("Initiating native CSP failed with error $error")
        }
    }
    private fun installLicenses() {
        logger.info("Installing CSP licenses...")
        // Установка триальных лицензий.
        val error = JniWrapper.license(CAdES_OCSP_LICENSE, CAdES_TSP_LICENSE)
        if (error != 0) {
            logger.error("CSP licenses not set, failed with error $error")
        }
    }
    private fun initMainCircle() {
        logger.info("Initiating main message circle...")
        // Цикл обработки nmcades'ом сообщений из javascript.
        val error = JniWrapper.main(context.applicationInfo.dataDir)
        if (error != 0) {
            logger.error("Main message circle failed with error $error")
        }
    }
}