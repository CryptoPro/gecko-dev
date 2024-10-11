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

        @JvmStatic
        external fun licenseCsp(csp_lic: String, user: String, company: String): Int

        @JvmStatic
        external fun errorMessage(error_code: Int): String?

        @JvmStatic
        external fun installPfx(pfx: String, password: String): Int

        @JvmStatic
        external fun installRootCert(cert: String): Int

    }
}