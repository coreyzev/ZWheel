package com.zwheel.core.protocol.debug

object BleDebugFixtureLoader {
    fun loadM1Fixture(name: String): List<String> {
        val path = "fixtures/m1/$name"
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing BLE debug fixture: $path"
        }
        return stream.bufferedReader().useLines { lines ->
            lines
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { line -> line.startsWith("#") }
                .toList()
        }
    }
}
