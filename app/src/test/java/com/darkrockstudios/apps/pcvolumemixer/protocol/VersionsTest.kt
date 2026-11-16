package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionsTest {

    @Test
    fun `the app speaks version 8`() {
        assertEquals(8, PROTOCOL_VERSION)
    }

    @Test
    fun `a lower or missing version means the server is older`() {
        assertEquals(OlderSide.SERVER, Versions.olderSide(7))
        assertEquals(OlderSide.SERVER, Versions.olderSide(0))
        assertEquals(OlderSide.SERVER, Versions.olderSide(null))
    }

    @Test
    fun `a higher version means the app is older`() {
        assertEquals(OlderSide.APP, Versions.olderSide(9))
    }

    @Test
    fun `a captured version 7 state is a mismatch with an older server`() {
        val line = ServerLineClassifier().classify(Samples.stateV7)

        assertEquals(ServerLine.VersionMismatch(7), line)
        assertEquals(OlderSide.SERVER, Versions.olderSide((line as ServerLine.VersionMismatch).version))
    }
}
