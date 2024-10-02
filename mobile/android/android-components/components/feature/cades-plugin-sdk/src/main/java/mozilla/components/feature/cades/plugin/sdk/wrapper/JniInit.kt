package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import mozilla.components.support.base.log.logger.Logger

import ru.CryptoPro.JCSP.NCSPConfig

class JniInit {
    companion object {

        private const val PRODUCT_NAME = "cades-plugin-sdk"
        private val logger = Logger(PRODUCT_NAME)

        private const val CADES_OCSP_LICENSE = "0A202-U0030-00ECW-RRLMF-UU2WK"
        private const val CADES_TSP_LICENSE = "TA200-G0030-00ECW-RRLNE-BTDVV"

        @JvmStatic
        fun initNativeCSP(context: Context) {
            logger.info("Initiating native CSP...")
            val error = NCSPConfig.init(context)
            if (error != NCSPConfig.CSP_INIT_OK) {
                logger.error("Initiating native CSP failed with error $error")
            }
        }

        @JvmStatic
        fun installLicenses() {
            logger.info("Installing CSP licenses...")
            // Установка триальных лицензий.
            val error = JniWrapper.license(CADES_OCSP_LICENSE, CADES_TSP_LICENSE)
            if (error != 0) {
                logger.error("CSP licenses not set, failed with error $error")
            }
        }

        @JvmStatic
        fun initMainCircle(context: Context) {
            logger.info("Initiating main message circle...")
            // Цикл обработки nmcades'ом сообщений из javascript.
            val error = JniWrapper.main(context.applicationInfo.dataDir)
            if (error != 0) {
                logger.error("Main message circle failed with error $error")
            }
        }
    }
}