package com.whocalled.android.screenshots

import android.app.Application

/** Bare Application for Robolectric: skips WorkManager/DB bootstrapping done by WhoCalledApplication. */
class ScreenshotApp : Application()
