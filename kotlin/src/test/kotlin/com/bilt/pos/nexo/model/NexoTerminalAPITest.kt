package com.bilt.pos.nexo.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NexoTerminalAPITest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private fun header(
        category: MessageCategoryType = MessageCategoryType.Payment,
        classType: MessageClassType = MessageClassType.Service,
        type: MessageTypeType = MessageTypeType.Request,
    ) = MessageHeader(
        messageCategory = category,
        messageClass = classType,
        messageType = type,
        poiid = "POI-1",
        saleID = "SALE-1",
        protocolVersion = "3.0",
        serviceID = "SVC-001",
    )

    @Nested
    inner class PaymentRequestSerialization {

        private val paymentRequest = NexoTerminalAPI(
            saleToPOIRequest = SaleToPOIRequest(
                messageHeader = header(),
                paymentRequest = PaymentRequest(
                    paymentTransaction = PaymentTransaction(
                        amountsReq = AmountsReq(
                            currency = "USD",
                            requestedAmount = 42.50,
                        ),
                    ),
                    saleData = SaleData(
                        saleTransactionID = TransactionIdentificationType(
                            timeStamp = "2025-01-15T10:30:00Z",
                            transactionID = "TXN-12345",
                        ),
                    ),
                ),
            ),
        )

        @Test
        fun `serialize payment request to JSON`() {
            val encoded = json.encodeToString(paymentRequest)

            encoded shouldBe json.encodeToString(
                json.decodeFromString<NexoTerminalAPI>(encoded)
            )
        }

        @Test
        fun `round-trip preserves all fields`() {
            val encoded = json.encodeToString(paymentRequest)
            val decoded = json.decodeFromString<NexoTerminalAPI>(encoded)

            val request = decoded.saleToPOIRequest.shouldNotBeNull()
            decoded.saleToPOIResponse.shouldBeNull()
            val header = request.messageHeader
            header.messageCategory shouldBe MessageCategoryType.Payment
            header.messageClass shouldBe MessageClassType.Service
            header.messageType shouldBe MessageTypeType.Request
            header.poiid shouldBe "POI-1"
            header.saleID shouldBe "SALE-1"
            header.protocolVersion shouldBe "3.0"

            val payment = request.paymentRequest!!
            payment.paymentTransaction.amountsReq.currency shouldBe "USD"
            payment.paymentTransaction.amountsReq.requestedAmount shouldBe 42.50
            payment.saleData.saleTransactionID.transactionID shouldBe "TXN-12345"
        }

        @Test
        fun `JSON uses SerialName keys`() {
            val encoded = json.encodeToString(paymentRequest)

            encoded.contains("\"SaleToPOIRequest\"") shouldBe true
            encoded.contains("\"MessageHeader\"") shouldBe true
            encoded.contains("\"PaymentRequest\"") shouldBe true
            encoded.contains("\"RequestedAmount\"") shouldBe true
            encoded.contains("\"Currency\"") shouldBe true
        }
    }

    @Nested
    inner class PaymentResponseDeserialization {

        private val responseJson = """
            {
              "SaleToPOIResponse": {
                "MessageHeader": {
                  "MessageCategory": "Payment",
                  "MessageClass": "Service",
                  "MessageType": "Response",
                  "POIID": "POI-1",
                  "SaleID": "SALE-1",
                  "ServiceID": "SVC-001"
                },
                "PaymentResponse": {
                  "Response": {
                    "Result": "Success"
                  },
                  "POIData": {
                    "POITransactionID": {
                      "TransactionID": "POI-TXN-99",
                      "TimeStamp": "2025-01-15T10:30:05Z"
                    }
                  },
                  "SaleData": {
                    "SaleTransactionID": {
                      "TransactionID": "TXN-12345",
                      "TimeStamp": "2025-01-15T10:30:00Z"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        @Test
        fun `deserialize successful payment response`() {
            val api = json.decodeFromString<NexoTerminalAPI>(responseJson)

            api.saleToPOIRequest.shouldBeNull()
            val resp = api.saleToPOIResponse.shouldNotBeNull()

            with(resp.messageHeader) {
                messageCategory shouldBe MessageCategoryType.Payment
                messageType shouldBe MessageTypeType.Response
            }

            val payment = resp.paymentResponse!!
            payment.response.result shouldBe ResultType.Success
            payment.response.errorCondition.shouldBeNull()
            payment.poiData.poiTransactionID.transactionID shouldBe "POI-TXN-99"
        }

        @Test
        fun `round-trip payment response`() {
            val decoded = json.decodeFromString<NexoTerminalAPI>(responseJson)
            val reEncoded = json.encodeToString(decoded)
            val reDecoded = json.decodeFromString<NexoTerminalAPI>(reEncoded)

            reDecoded shouldBe decoded
        }
    }

    @Nested
    inner class FailureResponse {

        private val failureJson = """
            {
              "SaleToPOIResponse": {
                "MessageHeader": {
                  "MessageCategory": "Payment",
                  "MessageClass": "Service",
                  "MessageType": "Response",
                  "POIID": "POI-1",
                  "SaleID": "SALE-1"
                },
                "PaymentResponse": {
                  "Response": {
                    "Result": "Failure",
                    "ErrorCondition": "Refusal",
                    "AdditionalResponse": "Insufficient funds"
                  },
                  "POIData": {
                    "POITransactionID": {
                      "TransactionID": "POI-TXN-100",
                      "TimeStamp": "2025-01-15T10:31:00Z"
                    }
                  },
                  "SaleData": {
                    "SaleTransactionID": {
                      "TransactionID": "TXN-12346",
                      "TimeStamp": "2025-01-15T10:30:55Z"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        @Test
        fun `deserialize failure with error condition`() {
            val resp = json.decodeFromString<NexoTerminalAPI>(failureJson)
            val response = resp.saleToPOIResponse!!.paymentResponse!!.response

            response.result shouldBe ResultType.Failure
            response.errorCondition shouldBe ErrorConditionType.Refusal
            response.additionalResponse shouldBe "Insufficient funds"
        }
    }

    @Nested
    inner class EnumSerialization {

        @Test
        fun `MessageCategoryType round-trips correctly`() {
            for (category in MessageCategoryType.entries) {
                val header = header(category = category)
                val encoded = json.encodeToString(header)
                val decoded = json.decodeFromString<MessageHeader>(encoded)
                decoded.messageCategory shouldBe category
            }
        }

        @Test
        fun `ResultType values serialize to expected names`() {
            val successJson = json.encodeToString(Response(result = ResultType.Success))
            successJson.contains("\"Success\"") shouldBe true

            val failureJson = json.encodeToString(
                Response(result = ResultType.Failure, errorCondition = ErrorConditionType.Cancel)
            )
            failureJson.contains("\"Failure\"") shouldBe true
            failureJson.contains("\"Cancel\"") shouldBe true
        }
    }

    @Nested
    inner class OptionalFields {

        @Test
        fun `optional fields are omitted from JSON when null`() {
            val minimal = NexoTerminalAPI()
            val encoded = json.encodeToString(minimal)

            encoded shouldBe "{}"
        }

        @Test
        fun `request with only required fields serializes correctly`() {
            val request = SaleToPOIRequest(
                messageHeader = MessageHeader(
                    messageCategory = MessageCategoryType.Login,
                    messageClass = MessageClassType.Service,
                    messageType = MessageTypeType.Request,
                    poiid = "T1",
                    saleID = "S1",
                ),
            )
            val encoded = json.encodeToString(request)
            val decoded = json.decodeFromString<SaleToPOIRequest>(encoded)

            decoded.messageHeader.poiid shouldBe "T1"
            decoded.paymentRequest.shouldBeNull()
            decoded.loginRequest.shouldBeNull()
            decoded.securityTrailer.shouldBeNull()
        }
    }

    @Nested
    inner class UnknownFieldsIgnored {

        @Test
        fun `extra JSON fields are ignored during deserialization`() {
            val jsonWithExtra = """
                {
                  "SaleToPOIResponse": {
                    "MessageHeader": {
                      "MessageCategory": "Admin",
                      "MessageClass": "Service",
                      "MessageType": "Response",
                      "POIID": "POI-1",
                      "SaleID": "SALE-1",
                      "FutureField": "should be ignored"
                    },
                    "AdminResponse": {
                      "Response": {
                        "Result": "Success"
                      }
                    }
                  }
                }
            """.trimIndent()

            val api = json.decodeFromString<NexoTerminalAPI>(jsonWithExtra)
            api.saleToPOIResponse!!.adminResponse!!.response.result shouldBe ResultType.Success
        }
    }
}
