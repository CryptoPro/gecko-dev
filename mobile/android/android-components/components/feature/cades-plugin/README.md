В папку cades-plugin распаковать дистрибутив android-csp-sdk, должно получиться
```
cades-plugin
     |
     |-- build.gradle
     |
     |-- proguard-rules.pro
     |
     |-- src
     |
     |-- android-native-csp
     |            |
     |            |-- aar
     |            |    |
     |            |    |-- csp-base*.aar
     |            |    |
     |            |    |-- csp-gui*.aar
     |            |
     |            |-- include
     |            |      |
     |            |      |-- *.h
     |            |
     |            |-- jniLibs
     |            |      |
     |            |      |-- {abi}
     |            |            |
     |            |            |-- *.so
     |            |
     |            |-- resources
     |                     |
     |                     |-- res
     |                          |
     |                          |-- raw
     |                               |
     |                               |-- config.ini
     |                               |
     |                               |-- license.ini
     |                               |
     |                               |-- root.sto
     |                               |
     |                               |-- digests32
     |                               |
     |                               |-- digests64
     |                               |
     |                               |-- digestsx86
     |                               |
     |                               |-- digestsx86_64

```
В папке android-native-csp/resources/res/raw в отладочных целях создать пустой файл dexdigests.