package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import ru.cprocsp.qrscanner.databinding.PasswordDialogBinding

class JniInit {
    companion object {

        private const val PRODUCT_NAME = "cades-plugin-sdk"
        private val logger = Logger(PRODUCT_NAME)

        private const val CADES_OCSP_LICENSE = "0A20B-83010-00KAN-9Q3BW-8EQDV"
        private const val CADES_TSP_LICENSE = "TA20D-H3010-00KAN-GF6KF-MVN4R"

        private const val RESULT_INSTALL_OK = 0
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
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uriManager = URIManagerFactory.getInstance(uri, context)
                    val listener: URIManagerFactory.ContentListener = URIManagerFactory.ContentListener { content, _ ->
                        content?.let { it ->
                            var messageId: Int = -1
                            var isError = false
                            val base64 = Base64()
                            val base64Data = base64.encodeAsString(it)
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
                                    checkPfxResultCode(context, base64Data, resultCode, onShowSnackbar)
                                }
                                is LicenseManager -> {
                                    val resultCode = JniWrapper.licenseCsp(it.toString(), "", "")
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

        @JvmStatic
        private fun checkPfxResultCode(context: Context, base64Data: String, resultCode: Int,
                                       onShowSnackbar: (String, Boolean) -> Unit) {
            val messageId: Int
            var isError = false
            when(resultCode) {
                RESULT_INSTALL_OK -> messageId = R.string.pfx_installation_success
                RESULT_ERROR_INVALID_PASSWORD -> {
                    messageId = R.string.pfx_invalid_password
                    isError = true
                    showPfxPasswordDialog(context, base64Data, onShowSnackbar)
                }
                else -> {
                    messageId = R.string.pfx_installation_failed
                    isError = true
                }
            }
            onShowSnackbar(context.getString(messageId), isError)
        }

        @JvmStatic
        private fun showPfxPasswordDialog(context: Context, base64Data: String,
                                          onShowSnackbar: (String, Boolean) -> Unit) {
            val passwordDialogBinding = PasswordDialogBinding.inflate(LayoutInflater.from(context))
            val title = context.getString(R.string.pfx_enter_password)
            MaterialAlertDialogBuilder(context, R.style.CryptoPro_MaterialAlertDialog).apply {
                setTitle(title)
                setView(passwordDialogBinding.root)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val password: String = passwordDialogBinding.etPassword.getText().toString()
                    var resultCode = JniWrapper.installPfx(base64Data, password)
                    if (resultCode == RESULT_ERROR_INVALID_PASSWORD)
                        resultCode = RESULT_INSTALL_ERROR
                    checkPfxResultCode(context, base64Data, resultCode, onShowSnackbar)
                }
                setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _ ->
                    dialog.cancel()
                    checkPfxResultCode(context, "", RESULT_INSTALL_ERROR, onShowSnackbar)
                }
                setCancelable(false)
                create()
            }.show()
        }
    }
}