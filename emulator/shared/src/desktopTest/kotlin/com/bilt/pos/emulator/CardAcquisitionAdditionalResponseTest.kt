package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.cardAcquisitionAdditionalResponse
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardAcquisitionAdditionalResponseTest {

    private val mapper = ObjectMapper()

    private fun extract(json: String) = cardAcquisitionAdditionalResponse(mapper.readTree(json))

    private val body =
        """"CardAcquisitionResponse":{"Response":{"Result":"Success","AdditionalResponse":"abc"}}"""

    @Test
    fun readsTheWireEnvelopeOfAnUnencryptedResponse() {
        assertEquals("abc", extract("""{"SaleToPOIResponse":{$body}}"""))
    }

    @Test
    fun readsTheDecryptedBodyOfAnEncryptedResponse() {
        assertEquals(
            "abc",
            extract("""{"MessageHeader":{"MessageCategory":"CardAcquisition"},$body}"""),
        )
    }

    @Test
    fun otherResponsesCarryNone() {
        assertNull(extract("""{"SaleToPOIResponse":{"DisplayResponse":{}}}"""))
        assertNull(extract("""{"DisplayResponse":{}}"""))
    }
}
