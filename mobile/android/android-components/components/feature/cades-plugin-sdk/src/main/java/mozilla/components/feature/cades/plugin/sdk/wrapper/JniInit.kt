package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import android.net.Uri
import mozilla.components.feature.cades.plugin.sdk.R
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

class JniInit {
    companion object {

        private const val PRODUCT_NAME = "cades-plugin-sdk"
        private val logger = Logger(PRODUCT_NAME)

        private const val CADES_OCSP_LICENSE = "0A20B-83010-00KAN-9Q3BW-8EQDV"
        private const val CADES_TSP_LICENSE = "TA20D-H3010-00KAN-GF6KF-MVN4R"

        private const val RESULT_INSTALL_OK = -1
        private const val RESULT_INSTALL_ERROR = -2
        private const val RESULT_ERROR_INVALID_PASSWORD: Int = -3

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
                    content?.let { it ->
                        var messageId: Int = -1
                        var isError = false
                        val base32 = Base32()
                        val base64 = Base64()
                        val base64Data = base64.encodeAsString(base32.decode(it))
                        when(uriManager) {
                            is RootCertificateManager -> {
                                val resultCode = JniWrapper.installRootCert(base64Data)
                                if (resultCode == RESULT_INSTALL_OK) {
                                    messageId = R.string.cert_installation_success
                                } else {
                                    messageId = R.string.cert_installation_failed
                                    isError = true
                                }
                            }
                            is CRLManager -> {
                            }
                            is IntermediateCertificateManager -> {
                            }
                            is PFXManager -> {
                                val resultCode = JniWrapper.installPfx(base64Data, "")
                                when(resultCode) {
                                    RESULT_INSTALL_OK -> messageId = R.string.pfx_installation_success
                                    RESULT_ERROR_INVALID_PASSWORD -> {}
                                    else -> {
                                        messageId = R.string.pfx_installation_failed
                                        isError = true
                                    }
                                }
                            }
                            is LicenseManager -> {
                                val resultCode = JniWrapper.licenseCsp(base64Data, "", "")
                                if (resultCode == RESULT_INSTALL_OK) {
                                    messageId = R.string.license_installation_success
                                } else {
                                    messageId = R.string.license_installation_failed
                                    isError = true
                                }
                            }
                            else -> {
                                messageId = R.string.InvalidURIFormat
                                isError = true
                            }
                        }
                        if (messageId != -1) onShowSnackbar(context.getString(messageId), isError)
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