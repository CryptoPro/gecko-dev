package mozilla.components.feature.cades.plugin.sdk.wrapper

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        fun importQr(context: Context, uri: Uri, onShowSnackbar: (String, Boolean) -> Unit) {
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
                                is PFXManager -> installPfx(context, Base64().encodeAsString(it), "", onShowSnackbar)
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
        private fun installPfx(context: Context, base64Data: String, password: String, onShowSnackbar: (String, Boolean) -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                val resultCode = JniWrapper.installPfx(base64Data, password)
                if (resultCode == RESULT_ERROR_INVALID_PASSWORD) {
                    withContext(Dispatchers.Main) {
                        showPfxPasswordDialog(context, base64Data, onShowSnackbar)
                    }
                }
                onShowSnackbar(resultCode, context.getString(R.string.pfx_installation_success), onShowSnackbar)
            }
        }

        @JvmStatic
        private fun showPfxPasswordDialog(context: Context, base64Data: String,
                                          onShowSnackbar: (String, Boolean) -> Unit) {
            val passwordDialogBinding = PasswordDialogBinding.inflate(LayoutInflater.from(context))
            AlertDialog.Builder(context).apply {
                setTitle(context.getString(R.string.pfx_enter_password))
                setView(passwordDialogBinding.root)
                setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _ -> dialog.cancel() }
                setPositiveButton(android.R.string.ok) { _, _ ->
                    val password: String = passwordDialogBinding.etPassword.getText().toString()
                    installPfx(context, base64Data, password, onShowSnackbar)
                }
                setCancelable(false)
                create()
            }.show()
        }

        private fun onShowSnackbar(resultCode: Int, messageSuccess: String, onShowSnackbar: (String, Boolean) -> Unit) {
            var message = JniWrapper.errorMessage(resultCode)
            if (message.isNullOrEmpty() && resultCode == RESULT_INSTALL_OK) {
                message = messageSuccess
            }
            message?.let { if (it.isNotEmpty()) onShowSnackbar(it, resultCode != RESULT_INSTALL_OK) }
        }
    }
}