package com.phoebe.app.platform

/** Human-readable app name shown in launchers and window chrome. */
fun appDisplayName(): String =
    if (isDebugBuild()) "Phoebe Debug" else "Phoebe"
