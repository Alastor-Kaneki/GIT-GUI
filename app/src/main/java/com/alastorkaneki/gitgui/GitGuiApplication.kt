package com.alastorkaneki.gitgui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

class GitGuiApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashStore.install(this)
        DownloadLog.beginSession(this)
        DownloadLog.append(this, "Application.attachBaseContext completed")
    }

    override fun onCreate() {
        DownloadLog.append(this, "Application.onCreate entered")
        super.onCreate()
        registerActivityLifecycleCallbacks(LifecycleLogger())
        DownloadLog.appendPreviousExitInfo(this)
        DownloadLog.append(this, "Application.onCreate completed")
    }

    private class LifecycleLogger : ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onCreate pre")
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onCreate completed")
        }

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onCreate post")
        }

        override fun onActivityStarted(activity: Activity) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onStart")
        }

        override fun onActivityResumed(activity: Activity) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onResume")
        }

        override fun onActivityPaused(activity: Activity) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onPause")
        }

        override fun onActivityStopped(activity: Activity) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onStop")
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onSaveInstanceState")
        }

        override fun onActivityDestroyed(activity: Activity) {
            DownloadLog.append(activity, "${activity.javaClass.name}.onDestroy")
        }
    }
}
