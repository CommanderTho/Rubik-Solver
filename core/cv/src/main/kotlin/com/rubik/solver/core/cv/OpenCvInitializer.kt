package com.rubik.solver.core.cv

import android.util.Log
import org.opencv.android.OpenCVLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe idempotent initializer for OpenCV native libraries.
 */
object OpenCvInitializer {
    private const val TAG = "OpenCvInitializer"
    private val initialized = AtomicBoolean(false)

    val isReady: Boolean
        get() = initialized.get()

    fun init(): Boolean {
        if (initialized.get()) return true
        return synchronized(this) {
            if (initialized.get()) {
                true
            } else {
                val ok = try {
                    OpenCVLoader.initLocal()
                } catch (t: Throwable) {
                    Log.e(TAG, "Không thể khởi tạo OpenCV native", t)
                    false
                }
                initialized.set(ok)
                if (ok) {
                    Log.i(TAG, "OpenCV initialized successfully")
                } else {
                    Log.e(TAG, "OpenCVLoader.initLocal() returned false")
                }
                ok
            }
        }
    }
}
