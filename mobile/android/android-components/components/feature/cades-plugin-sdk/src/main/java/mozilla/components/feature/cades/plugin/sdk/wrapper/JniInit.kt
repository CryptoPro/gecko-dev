package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import android.net.Uri
import mozilla.components.support.base.log.logger.Logger
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Base64
import ru.CryptoPro.JCSP.NCSPConfig
import ru.cprocsp.URIManager.CRLManager
import ru.cprocsp.URIManager.IntermediateCertificateManager
import ru.cprocsp.URIManager.LicenseManager
import ru.cprocsp.URIManager.PFXManager
import ru.cprocsp.URIManager.RootCertificateManager
import ru.cprocsp.URIManager.URIManagerFactory
import ru.cprocsp.qrscanner.R

class JniInit {
    companion object {

        private const val PRODUCT_NAME = "cades-plugin-sdk"
        private val logger = Logger(PRODUCT_NAME)

        private const val CADES_OCSP_LICENSE = "0A20F-Z3010-00K9F-E5X3Q-KAQZ9"
        private const val CADES_TSP_LICENSE = "TA20T-F3010-00K9F-94MYY-U841P"

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

        @JvmStatic
        fun importQr(context: Context, uri: Uri, onShowSnackbar: (String, Boolean) -> Unit) {
            try {
                val uriManager = URIManagerFactory.getInstance(uri, context)
                val listener: URIManagerFactory.ContentListener = URIManagerFactory.ContentListener { content, _ ->
                    content?.let {
                        //val base32 = Base32()
                        //val base64 = Base64()
                        //val base64Data = base64.encode(base32.decode(it))
                        when(uriManager) {
                            is RootCertificateManager -> {
                            }
                            is CRLManager -> {

                            }
                            is IntermediateCertificateManager -> {

                            }
                            is PFXManager -> {

                            }
                            is LicenseManager -> {

                            }
                            else -> onShowSnackbar(context.getString(R.string.InvalidURIFormat), true)
                        }
                    }
                }
                uriManager.setListener(listener)
                uriManager.addContent(context, uri)
            } catch (e : Exception) {
                onShowSnackbar(e.message ?: context.getString(R.string.InvalidURIFormat), true)
            }
        }
    }
}