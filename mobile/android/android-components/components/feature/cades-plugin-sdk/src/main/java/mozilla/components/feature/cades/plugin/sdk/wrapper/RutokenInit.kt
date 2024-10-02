package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import mozilla.components.support.base.log.logger.Logger
import ru.rutoken.rtpcsc.RtPcsc

class RutokenInit {
    companion object {
        private const val PRODUCT_NAME = "cades-plugin-sdk-rutoken"
        private val logger = Logger(PRODUCT_NAME)
        @JvmStatic
        fun init(context: Context) {
            logger.info("Loading rutoken support...")
            if (!RtPcsc.setAppContext(context)) {
                logger.error("Loading rutoken failed.")
            }
        }
    }
}