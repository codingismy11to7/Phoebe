package com.phoebe.app.testing

import java.util.Locale
import org.junit.Assume.assumeTrue

fun assumeLinux() {
    val os = System.getProperty("os.name").lowercase(Locale.US)
    assumeTrue("Linux-only desktop test", "linux" in os)
}
