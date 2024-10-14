package mozilla.components.feature.cades.plugin

import mozilla.components.concept.engine.webextension.Port
import mozilla.components.feature.cades.plugin.sdk.wrapper.JniWrapper
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONObject

class CAdESMessageReader(val logger: Logger): Thread() {
    private var port: Port? = null
    fun setPort(p: Port) {
        synchronized(this) {
            if (port != p) {
                logger.debug("setPort($p) for session ${p.engineSession}")
                port = p
            }
        }
    }
    override fun run() {
        logger.debug("Starting reader thread with port $port for session ${port?.engineSession}")
        while (!isInterrupted) {
            val e = JniWrapper.read();
            val s = String(e)
            logger.debug("Read $e and post to port $port with session ${port?.engineSession}")
            synchronized(this) {
                port?.postMessage(JSONObject(s))
            }
        }
    }
}