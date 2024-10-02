package mozilla.components.feature.cades.plugin

import android.content.Context
import androidx.annotation.VisibleForTesting
import mozilla.components.feature.cades.plugin.wrapper.JniWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.kotlinx.coroutines.flow.filterChanged
import mozilla.components.support.webextensions.WebExtensionController

class CAdESPluginFeature(
    private val context: Context,
    private val runtime: WebExtensionRuntime,
    private val store: BrowserStore,
) : LifecycleAwareFeature {

    private var scope: CoroutineScope? = null

    @VisibleForTesting
    // This is an internal var to make it mutable for unit testing purposes only
    internal var extensionController = WebExtensionController(
        CAdES_PLUGIN_EXTENSION_ID,
        CAdES_PLUGIN_EXTENSION_URL,
        CAdES_PLUGIN_MESSAGING_ID,
    )

    override fun start() {
        extensionController.registerBackgroundMessageHandler(CAdESPluginMessageHandler(), CAdES_PLUGIN_CONTENT_BACKGROUND_ID)
        extensionController.install(
            runtime,
            onSuccess = {
                    it ->
                        // Нужно дождаться завершения инициализации. Она делается один раз.
                        runBlocking {
                            withContext(Dispatchers.IO) {
                                CAdESPlugin.init(context, logger)
                            }
                        }
                        // Реакция на изменения на вкладках.
                        store.flowScoped { flow ->
                            flow.map { it.tabs }
                            .filterChanged { it.engineState.engineSession }
                            .collect { tab ->
                                val engineSession = tab.engineState.engineSession ?: return@collect
                                if (it.hasContentMessageHandler(engineSession, CAdES_PLUGIN_MESSAGING_ID)) {
                                    return@collect
                                }
                                logger.debug("registerContentMessageHandler with session $engineSession")
                                extensionController.registerContentMessageHandler(engineSession, CAdESPluginMessageHandler(), CAdES_PLUGIN_MESSAGING_ID)
                            }
                        }
                    logger.debug("Installed CAdES Plug-in web extension: ${it.id}")
            },
            onError = { throwable ->
                logger.error("Failed to install CAdES Plug-in web extension: ", throwable)
            },)
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
    }

    private class CAdESPluginMessageHandler(
        private val productName: String = PRODUCT_NAME,
    ) : MessageHandler {
        override fun onPortConnected(port: Port) {
            logger.debug("onPortConnected($port) for session ${port.engineSession}")
            reader.setPort(port) // задаем порт
        }
        override fun onPortMessage(message: Any, port: Port) {
            reader.setPort(port) // актуализируем порт
            val e = message.toString();
            logger.debug("onPortMessage($e, $port) for session ${port.engineSession}");
            JniWrapper.write(e, 0)
        }
        override fun onPortDisconnected(port: Port) {
            logger.debug("onPortDisconnected($port) for session ${port.engineSession}")
        }
    }

}