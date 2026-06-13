plugins {
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("PhoebeDatabase") {
            packageName.set("com.phoebe.app.db")
            generateAsync.set(true)
        }
    }
}
