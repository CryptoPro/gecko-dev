var chrome_ext_id = "iifchhfnnmpdbibifmljnfjhpififfog";
var opera_ext_id = "epebfcehmdedogndhlcacafjaacknbcm";

function isExtensionNeeded() {
  if (isIE()) return false;
  if (browserSpecs.name == 'Edge') { return true; }
  if (browserSpecs.name == 'Opera') { if (browserSpecs.version >= 33) { return true; } else { return false; } }
  if (browserSpecs.name == 'Firefox') { if (browserSpecs.version >= 52) { return true; } else { return false; } }
  if (browserSpecs.name == 'Chrome') { if (browserSpecs.version >= 42) { return true; } else { return false; } }
  if (browserSpecs.name == 'Safari') { if (browserSpecs.version >= 11) { return true; } else { return false; } }
  return true;
}

if (!isExtensionNeeded()) {
  window.cadesplugin_extension_loaded = true;
  setStateForExtension(Colors.SUCCESS, "Расширение не требуется");
}

var canPromise = !!window.Promise;
if (isEdge()) {
  setStateForExtension(Colors.ERROR, "Расширение не загружено");
  ShowEdgeNotSupported();
} else {
  if (canPromise) {
    cadesplugin.then(
      function () {
        Common_CheckForPlugIn();
      },
      function (error) {
        if (window.cadesplugin_extension_loaded) {
          setStateForPlugin(Colors.FAIL, error);
        }
        if (isYandex()) {
          var fileref = document.createElement('script');
          fileref.setAttribute("type", "text/javascript");
          fileref.setAttribute("src", "chrome-extension://iifchhfnnmpdbibifmljnfjhpififfog/nmcades_plugin_api.js");
          fileref.onload = function () {
            try {
              window.addEventListener('load', function () {
                cadesplugin.get_extension_id(function (ext_id) {
                  if (ext_id === chrome_ext_id) {
                    setStateForExtension(Colors.UPDATE,
                      "Для работы в Yandex Browser требуется расширение из Opera Store");
                    extUrl = "https://addons.opera.com/en/extensions/details/cryptopro-extension-for-cades-browser-plug-in";
                    setInnerText("ExtensionSolution", "<a href='" + extUrl + "'>Загрузить</a>", true);
                  }
                });
              })
            }
            catch (err) { }
          };
          document.getElementsByTagName("head")[0].appendChild(fileref);
        }
      }
    );
  } else {
    window.addEventListener(
      "message",
      function (event) {
        if (event.data == "cadesplugin_loaded") {
          CheckForPlugIn_NPAPI();
        } else if (event.data == "cadesplugin_load_error") {
          if (window.cadesplugin_extension_loaded) {
            setStateForPlugin(Colors.FAIL, "Плагин не загружен");
          }
        }
      },
      false
    );
    window.postMessage("cadesplugin_echo_request", "*");
  }
}
