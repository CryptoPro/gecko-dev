#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <string>
#include <istream>

#include <sys/types.h>
#include <sys/stat.h>

#define TAG "gecko-wrapper"

extern "C" int main_wrapper(int pipe_in, int pipe_out);
extern "C" const char* read_wrapper();
extern "C" int write_wrapper(const char* request, int length);
extern "C" int license_wrapper(const char* ocsp_lic, const char* tsp_lic);
extern "C" int install_pfx_wrapper(const char* pfx, const char* password);
extern "C" int install_root_cert_wrapper(const char* cert);
extern "C" int license_csp_wrapper(const char* csp_lic, const char* user, const char* company);
extern "C" const char* error_message_wrapper(int code);


static int fd_in = 0, fd_out = 0;

extern "C"
JNIEXPORT jint JNICALL
Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_main(JNIEnv *env, jclass clazz, jstring jPath) {
    int error;
    const char *pszPath = nullptr;
    std::string path, inPath, outPath;
    int in, out;
    if (jPath != nullptr) {
        pszPath = env->GetStringUTFChars(jPath, JNI_FALSE);
    }
    if (pszPath == nullptr) {
        return JNI_EINVAL;
    }
    path = pszPath;
    inPath = path + "/in";
    outPath = path + "/out";
    in = mkfifo(inPath.c_str(), S_IRUSR | S_IWUSR);
    if (in) {
        error = errno;
        if (error != EEXIST) {
            __android_log_print(ANDROID_LOG_VERBOSE, TAG, "mkfifo(%s) failed, error %d\n", inPath.c_str(), error);
            goto end;
        }
    }
    fd_in = open(inPath.c_str(), O_RDWR);
    if (fd_in < 0) {
        error = errno;
        __android_log_print(ANDROID_LOG_VERBOSE, TAG, "open(%s) failed, error %d\n", inPath.c_str(), error);
        goto end;
    }
    out = mkfifo(outPath.c_str(), S_IRUSR | S_IWUSR);
    if (out) {
        error = errno;
        if (error != EEXIST) {
            __android_log_print(ANDROID_LOG_VERBOSE, TAG, "mkfifo(%s) failed, error %d\n", outPath.c_str(), error);
            return (jint) out;
        }
    }
    fd_out = open(outPath.c_str(), O_RDWR);
    if (fd_out < 0) {
        error = errno;
        __android_log_print(ANDROID_LOG_VERBOSE, TAG, "open(%s) failed, error %d\n", outPath.c_str(), error);
        return (jint)errno;
    }
    error = (jint)main_wrapper(fd_in, fd_out);
end:
    return (jint)error;
}

extern "C"
JNIEXPORT jint JNICALL
Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_close(JNIEnv *env, jclass clazz, jstring jPath) {
    if (fd_in) {
        close(fd_in);
        fd_in = 0;
    }
    if (fd_out) {
        close(fd_out);
        fd_out = 0;
    }
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_read(JNIEnv *env, jclass clazz) {
    auto result = read_wrapper();
    if (result != nullptr) {
        return env->NewStringUTF(result);
    }
    return nullptr;
}
extern "C"
JNIEXPORT jint JNICALL
Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_write(JNIEnv *env, jclass clazz, jstring jMessage, jint jFlags) {
    const char *message = nullptr;
    jint messageLen = 0;
    if (jMessage != nullptr) {
        message = env->GetStringUTFChars(jMessage, JNI_FALSE);
        messageLen = env->GetStringUTFLength(jMessage);
    }
    auto result = write_wrapper(message, messageLen);
    if (message != nullptr) {
        env->ReleaseStringUTFChars(jMessage, message);
    }
    return result;
}
extern "C"
JNIEXPORT jint JNICALL
Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_license(JNIEnv *env, jclass clazz, jstring ocsp_lic, jstring tsp_lic) {
    const char *szOcspLicense = nullptr;
    const char *szTspLicense = nullptr;
    jint ocspLicenseLen = 0;
    if (ocsp_lic != nullptr) {
        szOcspLicense = env->GetStringUTFChars(ocsp_lic, JNI_FALSE);
        ocspLicenseLen = env->GetStringUTFLength(ocsp_lic);
    }
    jint tspLicenseLen = 0;
    if (tsp_lic != nullptr) {
        szTspLicense = env->GetStringUTFChars(tsp_lic, JNI_FALSE);
        tspLicenseLen = env->GetStringUTFLength(tsp_lic);
    }
    auto result = license_wrapper(szOcspLicense, szTspLicense);
    if (szOcspLicense != nullptr) {
        env->ReleaseStringUTFChars(ocsp_lic, szOcspLicense);
    }
    if (szOcspLicense != nullptr) {
        env->ReleaseStringUTFChars(tsp_lic, szTspLicense);
    }
    return result;
}
extern "C"
    JNIEXPORT jint JNICALL
    Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_licenseCsp(JNIEnv *env, jclass clazz, jstring csp_lic, jstring user, jstring company) {
  const char *szCspLicense = nullptr;
  const char *szUser = nullptr;
  const char *szCompany = nullptr;
  if (csp_lic != nullptr) {
    szCspLicense = env->GetStringUTFChars(csp_lic, JNI_FALSE);
  }
  if (user != nullptr) {
    szUser = env->GetStringUTFChars(user, JNI_FALSE);
  }
  if (company != nullptr) {
    szCompany = env->GetStringUTFChars(company, JNI_FALSE);
  }
  auto result = license_csp_wrapper(szCspLicense, szUser, szCompany);
  if (szCspLicense != nullptr) {
    env->ReleaseStringUTFChars(csp_lic, szCspLicense);
  }
  if (szUser != nullptr) {
    env->ReleaseStringUTFChars(user, szUser);
  }
  if (szCompany != nullptr) {
    env->ReleaseStringUTFChars(company, szCompany);
  }
  return result;
}
extern "C"
    JNIEXPORT jint JNICALL
    Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_installPfx(JNIEnv *env, jclass clazz, jstring pfx, jstring password) {
  const char *szPfx = nullptr;
  const char *szPassword = nullptr;
  if (pfx != nullptr) {
    szPfx = env->GetStringUTFChars(pfx, JNI_FALSE);
  }
  if (password != nullptr) {
    szPassword = env->GetStringUTFChars(password, JNI_FALSE);
  }
  auto result = install_pfx_wrapper(szPfx, szPassword);
  if (szPfx != nullptr) {
    env->ReleaseStringUTFChars(pfx, szPfx);
  }
  if (szPassword != nullptr) {
    env->ReleaseStringUTFChars(password, szPassword);
  }
  return result;
}
extern "C"
    JNIEXPORT jint JNICALL
    Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_installRootCert(JNIEnv *env, jclass clazz, jstring cert) {
  const char *szCert = nullptr;
  if (cert != nullptr) {
    szCert = env->GetStringUTFChars(cert, JNI_FALSE);
  }
  auto result = install_root_cert_wrapper(szCert);
  if (szCert != nullptr) {
    env->ReleaseStringUTFChars(cert, szCert);
  }
  return result;
}

extern "C"
    JNIEXPORT jstring JNICALL
    Java_mozilla_components_feature_cades_plugin_sdk_wrapper_JniWrapper_errorMessage(JNIEnv *env, jclass clazz, jint code) {
  auto result = error_message_wrapper(code);
  if (result != nullptr) {
    return env->NewStringUTF(result);
  }
  return nullptr;
}
