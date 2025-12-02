package com.github.opentube

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.libretube.helpers.NewPipeExtractorInstance
import com.github.opentube.error.DebugActivity
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.File
import java.io.InterruptedIOException
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main application class for OpenTube.
 * Handles application-level initialization including downloader setup with proper initialization,
 * comprehensive crash handling and reporting, RxJava error handling, first run detection, and
 * service initialization.
 *
 * Licensed under GNU General Public License (GPL) version 3 or later.
 */
class App : Application() {

    private var crashDirectory: File? = null
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        initNewPipe()

        Log.i(TAG, "OpenTube Application starting...")

        try {
            initializeCrashDirectory()
            setupCrashHandler()
            setupRxJavaErrorHandler()

            isInitialized = true
            Log.i(TAG, "OpenTube Application initialized successfully")

            if (isFirstRun) {
                Log.i(TAG, "First run detected - showing welcome screen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OpenTube Application", e)
        }
    }

    /**
     * Initialize NewPipe Extractor instance.
     */
    private fun initNewPipe() {
        try {
            NewPipeExtractorInstance.init()
            Log.i(TAG, "NewPipe Extractor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe Extractor", e)
        }
    }

    /**
     * Initialize crash directory for storing crash reports.
     */
    private fun initializeCrashDirectory() {
        try {
            crashDirectory = File(filesDir, "crashes").apply {
                if (!exists()) {
                    if (mkdirs()) {
                        Log.d(TAG, "Crash directory created: $absolutePath")
                    } else {
                        Log.w(TAG, "Failed to create crash directory")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating crash directory", e)
        }
    }

    /**
     * Sets up the custom crash handler for the application. This handler captures uncaught
     * exceptions and saves crash reports to the crash directory.
     */
    private fun setupCrashHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in crash handler", t)
            } finally {
                defaultExceptionHandler?.uncaughtException(thread, throwable) ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }
            }
        }

        Log.d(TAG, "Crash handler initialized")
    }

    /**
     * Handles crash by saving report and launching debug activity.
     *
     * @param thread the thread where crash occurred
     * @param throwable the exception that caused the crash
     */
    private fun handleCrash(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "App crashed on thread: ${thread.name}", throwable)

        try {
            val crashReport = buildCrashReport(thread, throwable)
            saveCrashReport(crashReport)
            launchDebugActivity(crashReport)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle crash properly", e)
        }
    }

    /**
     * Builds a comprehensive crash report with device and app information.
     *
     * @param thread the thread where crash occurred
     * @param throwable the exception that caused the crash
     * @return formatted crash report string
     */
    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return buildString {
            appendLine("=".repeat(50))
            appendLine("       OpenTube Crash Report")
            appendLine("=".repeat(50))
            appendLine()

            appendLine("Time: ${dateFormat.format(Date())}")

            appendLine("Thread: ${thread.name} (ID: ${thread.id})")

            appendLine("App Version: $versionName")
            appendLine("Package: $packageName")

            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

            appendLine("Manufacturer: ${android.os.Build.MANUFACTURER}")
            appendLine("Model: ${android.os.Build.MODEL}")
            appendLine("Device: ${android.os.Build.DEVICE}")

            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            appendLine("Memory: ${totalMemory - freeMemory}/$maxMemory MB")
            appendLine()

            appendLine("Exception Type: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "No message"}")
            appendLine()

            appendLine("Stack Trace:")
            appendLine("-".repeat(50))
            appendLine(throwable.stackTraceToString())

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("Caused by: ${cause.javaClass.name}")
                appendLine("Message: ${cause.message ?: "No message"}")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
            }

            appendLine()
            appendLine("=".repeat(50))
        }
    }

    /**
     * Saves crash report to file in the crash directory.
     *
     * @param crashReport the formatted crash report
     */
    private fun saveCrashReport(crashReport: String) {
        val directory = crashDirectory
        if (directory == null || !directory.exists()) {
            Log.w(TAG, "Crash directory not available")
            return
        }

        try {
            val fileName = "crash_${System.currentTimeMillis()}.txt"
            val crashFile = File(directory, fileName)

            crashFile.writeText(crashReport)

            Log.i(TAG, "Crash report saved: ${crashFile.name}")
            cleanupOldReports()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report", e)
        }
    }

    /**
     * Removes old crash reports, keeping only the newest MAX_CRASH_REPORTS files.
     */
    private fun cleanupOldReports() {
        try {
            val directory = crashDirectory
            if (directory == null || !directory.exists()) return

            val files = directory.listFiles() ?: return
            if (files.size <= MAX_CRASH_REPORTS) return

            val sortedFiles = files.sortedBy { it.lastModified() }

            val toDelete = files.size - MAX_CRASH_REPORTS
            sortedFiles.take(toDelete).forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted old crash report: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup crash reports", e)
        }
    }

    /**
     * Launches the debug activity to display crash details to the user.
     *
     * @param crashReport the crash report to display
     */
    private fun launchDebugActivity(crashReport: String) {
        try {
            Intent(this, DebugActivity::class.java).apply {
                putExtra("CRASH_REPORT", crashReport)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                startActivity(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch DebugActivity", e)
        }
    }

    /**
     * Gets the app version name from package info.
     *
     * @return version name or "Unknown" if not available
     */
    private val versionName: String?
        get() = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version name", e)
            "Unknown"
        }

    /**
     * Handles undeliverable RxJava exceptions globally. This method ignores network-related
     * exceptions to prevent unnecessary crashes while logging other undeliverable exceptions
     * for debugging purposes.
     */
    private fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { e ->
            var error: Throwable = e

            if (error is UndeliverableException) {
                error = error.cause ?: error
            }

            when (error) {
                is java.io.IOException,
                is SocketException,
                is InterruptedException,
                is InterruptedIOException -> {
                    Log.d(TAG, "Ignored RxJava network exception: ${error.javaClass.simpleName}")
                    return@setErrorHandler
                }
            }

            if (error is IllegalStateException &&
                error.message?.contains("disposed") == true
            ) {
                Log.d(TAG, "Ignored RxJava disposed exception")
                return@setErrorHandler
            }

            Log.e(TAG, "RxJava undeliverable exception", error)
        }

        Log.d(TAG, "RxJava error handler initialized")
    }

    /**
     * Checks if this is the first app run. The method sets the flag to false after the first check
     * to ensure subsequent calls return false.
     *
     * @return true if first run, false otherwise
     */
    val isFirstRun: Boolean
        get() {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val isFirst = prefs.getBoolean(KEY_FIRST_RUN, true)

            if (isFirst) {
                prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
            }

            return isFirst
        }

    /**
     * Gets all saved crash report files sorted by date with newest first.
     *
     * @return array of crash report files, empty array if none exist
     */
    val crashReports: Array<File>
        get() {
            val directory = crashDirectory
            return if (directory != null && directory.exists()) {
                directory.listFiles()?.sortedByDescending { it.lastModified() }?.toTypedArray()
                    ?: emptyArray()
            } else {
                emptyArray()
            }
        }

    /**
     * Clears all saved crash reports from the crash directory.
     *
     * @return number of reports deleted
     */
    fun clearCrashReports(): Int {
        var deletedCount = 0

        try {
            val directory = crashDirectory
            if (directory == null || !directory.exists()) return 0

            directory.listFiles()?.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            Log.i(TAG, "Cleared $deletedCount crash reports")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash reports", e)
        }

        return deletedCount
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "OpenTube Application terminating...")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning received")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Trim memory level: $level")
    }

    companion object {
        private const val TAG = "App"
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_FIRST_RUN = "key_first_run"
        private const val MAX_CRASH_REPORTS = 5

        @Volatile
        private var instance: App? = null

        /**
         * Gets the application singleton instance.
         *
         * @return App instance
         * @throws IllegalStateException if application not initialized
         */
        fun getInstance(): App {
            return instance ?: throw IllegalStateException("App not initialized")
        }

        /**
         * Gets the application context safely.
         *
         * @return application context
         * @throws IllegalStateException if application not initialized
         */
        val appContext: Context
            get() = getInstance().applicationContext
    }
}