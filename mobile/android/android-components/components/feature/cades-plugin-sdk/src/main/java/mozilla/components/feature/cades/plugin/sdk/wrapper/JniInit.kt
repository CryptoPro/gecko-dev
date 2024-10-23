package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.cades.plugin.sdk.R
import mozilla.components.support.base.log.logger.Logger
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

        private const val CADES_OCSP_LICENSE = "0A20Y-L3010-00KCZ-U49HK-1L7P6"
        private const val CADES_TSP_LICENSE = "TA20X-Z3010-00KCZ-UU881-A2F3M"

        private const val RESULT_INSTALL_OK = 0x00000000
        private const val RESULT_ERROR_INVALID_PASSWORD = 0x80070056.toInt()

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
        fun importQr(context: Context, uri: Uri, onShowSnackbar: (String, Boolean) -> Unit, onShowPfxPasswordDialog: ((String) -> Unit) -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uriManager = URIManagerFactory.getInstance(uri, context)
                    val listener: URIManagerFactory.ContentListener = URIManagerFactory.ContentListener { content, _ ->
                        content?.let { it ->
                            when(uriManager) {
                                is RootCertificateManager -> {
                                    val resultCode = JniWrapper.installRootCert(Base64().encodeAsString(it))
                                    onShowSnackbar(resultCode, context.getString(R.string.cert_installation_success), onShowSnackbar)
                                }
                                is PFXManager -> installPfx(context, Base64().encodeAsString(it), "", true,
                                    onShowSnackbar, onShowPfxPasswordDialog)
                                is LicenseManager -> {
                                    val resultCode = JniWrapper.licenseCsp(it.toString(Charsets.UTF_8), "", "")
                                    onShowSnackbar(resultCode, context.getString(R.string.license_installation_success), onShowSnackbar)
                                }
                                is CRLManager -> {}
                                is IntermediateCertificateManager -> {}
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

        @JvmStatic
        private fun installPfx(context: Context, base64Data: String, password: String, isFirstCall: Boolean,
                               onShowSnackbar: (String, Boolean) -> Unit, onShowPfxPasswordDialog: ((String) -> Unit) -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                val resultCode = JniWrapper.installPfx(base64Data, password)
                if (isFirstCall && resultCode == RESULT_ERROR_INVALID_PASSWORD) {
                    onShowPfxPasswordDialog { newPassword ->
                        installPfx(
                            context,
                            base64Data,
                            newPassword,
                            false,
                            onShowSnackbar,
                            onShowPfxPasswordDialog
                        )
                    }
                    return@launch
                }
                onShowSnackbar(resultCode, context.getString(R.string.pfx_installation_success), onShowSnackbar)
            }
        }

        private fun onShowSnackbar(resultCode: Int, messageSuccess: String, onShowSnackbar: (String, Boolean) -> Unit) {
            var message = JniWrapper.errorMessage(resultCode)
            val resultOk = resultCode == RESULT_INSTALL_OK
            if (message.isNullOrEmpty() && resultOk) message = messageSuccess
            if (!message.isNullOrEmpty()) {
                if (!resultOk) message += "(0x${Integer.toHexString(resultCode)})"
                onShowSnackbar(message, !resultOk)
            }
        }
    }
}
