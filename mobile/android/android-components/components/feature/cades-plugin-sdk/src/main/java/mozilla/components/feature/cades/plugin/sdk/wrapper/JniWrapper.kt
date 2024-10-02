package mozilla.components.feature.cades.plugin.sdk.wrapper

class JniWrapper {
    companion object {

        init {
            System.loadLibrary("cades_plugin_wrapper")
        }

        @JvmStatic
        external fun main(path: String): Int

        @JvmStatic
        external fun read(): String

        @JvmStatic
        external fun write(message: String, flags: Int): Int

        @JvmStatic
        external fun license(ocsp_lic: String, tsp_lic: String): Int

        @JvmStatic
        external fun close(path: String): Int

    }
}