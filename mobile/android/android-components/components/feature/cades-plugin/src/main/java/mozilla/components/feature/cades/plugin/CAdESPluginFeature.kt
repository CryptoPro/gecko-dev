package mozilla.components.feature.cades.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.feature.cades.plugin.sdk.wrapper.JniInit
import mozilla.components.feature.cades.plugin.sdk.wrapper.JniWrapper
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.kotlinx.coroutines.flow.filterChanged
import mozilla.components.support.webextensions.WebExtensionController

class CAdESPluginFeature(
    private val context: Context,
    private val runtime: WebExtensionRuntime,
    private val store: BrowserStore,
    private val launchQr: () -> Unit,
    private val onShowSnackbar: (String, Boolean) -> Unit,
    private val onShowPfxPasswordDialog: ((String) -> Unit) -> Unit
) : LifecycleAwareFeature, ActivityResultHandler {

    private var scope: CoroutineScope? = null

    @VisibleForTesting
    // This is an internal var to make it mutable for unit testing purposes only
    internal var extensionController = WebExtensionController(
        CAdES_PLUGIN_EXTENSION_ID,
        CAdES_PLUGIN_EXTENSION_URL,
        CAdES_PLUGIN_MESSAGING_ID,
    )

    override fun start() {
        extensionController.registerBackgroundMessageHandler(CAdESPluginMessageHandler(launchQr = launchQr), CAdES_PLUGIN_CONTENT_BACKGROUND_ID)
        extensionController.install(
            runtime,
            onSuccess = {
                // Нужно дождаться завершения инициализации. Она делается один раз.
                runBlocking {
                    withContext(Dispatchers.IO) {
                        CAdESPlugin.init(context)
                    }
                }
                // Реакция на изменения на вкладках.
                scope = store.flowScoped { flow ->
                    flow.mapNotNull { it.tabs  }
                        .filterChanged { it.engineState.engineSession }
                        .collect {
                            it.engineState.engineSession?.let { engineSession ->
                                logger.debug("registerContentMessageHandler with session $engineSession")
                                extensionController.registerContentMessageHandler(engineSession, CAdESPluginMessageHandler(launchQr = launchQr), CAdES_PLUGIN_MESSAGING_ID)
                            }
                        }
                }
                logger.debug("Installed CAdES Plug-in web extension: ${it.id}")
            },
            onError = { throwable ->
                logger.error("Failed to install CAdES Plug-in web extension: ", throwable)
            })

    }

    override fun stop() {
        scope?.cancel()
    }

    companion object {
        internal const val PRODUCT_NAME = "cades-plugin"
        private val logger = Logger(PRODUCT_NAME)
        internal const val CAdES_PLUGIN_EXTENSION_ID = "ru.cryptopro.nmcades@cryptopro.ru"
        internal const val CAdES_PLUGIN_EXTENSION_URL = "resource://android/assets/extensions/cades-plugin/"
        internal const val CAdES_PLUGIN_MESSAGING_ID = "ru.cryptopro.nmcades.content"
        internal const val CAdES_PLUGIN_CONTENT_BACKGROUND_ID = "ru.cryptopro.nmcades"
        // Вечный поток читателя сообщений из nmcades для передачи в javascript.
        internal val reader: CAdESMessageReader by lazy {
            CAdESMessageReader(logger).also {
                it.start()
            }
        }
        private const val LAUNCH_QR = "LAUNCH_QR_SCANNER"
        // The Qr request code
        const val QR_REQUEST = 111123
    }

    override fun onActivityResult(requestCode: Int, data: Intent?, resultCode: Int): Boolean {
        if (requestCode == QR_REQUEST ) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { uri ->
                    JniInit.importQr(
                        context = context,
                        uri = uri,
                        onShowSnackbar = { text, isError -> onShowSnackbar(text, isError) },
                        onShowPfxPasswordDialog = { password -> onShowPfxPasswordDialog(password) }
                    )
                }
            }
            return true
        }
        return false
    }

    private class CAdESPluginMessageHandler(
        private val productName: String = PRODUCT_NAME,
        private val launchQr: () -> Unit
    ) : MessageHandler {
        override fun onPortConnected(port: Port) {
            logger.debug("onPortConnected($port) for session ${port.engineSession}")
            reader.setPort(port) // задаем порт
        }
        override fun onPortMessage(message: Any, port: Port) {
            reader.setPort(port) // актуализируем порт
            val e = message.toString()
            if (e.equals(LAUNCH_QR, true)) {
                launchQr()
                return
            }
            logger.debug("onPortMessage($e, $port) for session ${port.engineSession}")
            JniWrapper.write(e, 0)
        }
        override fun onPortDisconnected(port: Port) {
            logger.debug("onPortDisconnected($port) for session ${port.engineSession}")
        }
    }

}