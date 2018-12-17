package com.fileee.oihAdapter

import io.kotlintest.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    // all code is stateless hence parallelism is no problem at all
    override fun parallelism(): Int = Runtime.getRuntime().availableProcessors()
}