package dev.ide.core.gradle

/** Minimal runtime detector; avoids importing IDE SDK-download/UI services into the build host. */
internal object KlencodRuntimeDetector {
    fun isAndroidRuntime(vmName: String?, vendor: String?): Boolean =
        vmName.orEmpty().contains("Dalvik", ignoreCase = true) ||
            vendor.orEmpty().contains("Android", ignoreCase = true)
}
