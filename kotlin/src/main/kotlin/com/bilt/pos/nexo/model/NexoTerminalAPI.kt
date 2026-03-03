/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json            = Json { allowStructuredMapKeys = true }
// val nexoTerminalAPI = json.parse(NexoTerminalAPI.serializer(), jsonString)

package com.bilt.pos.nexo.model

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Request message of the EPAS Sale To POI protocol.
 *
 * Response message of the EPAS Sale To POI protocol.
 */
@Serializable
data class NexoTerminalAPI (
    @SerialName("SaleToPOIRequest")
    val saleToPOIRequest: SaleToPOIRequest? = null,

    @SerialName("SaleToPOIResponse")
    val saleToPOIResponse: SaleToPOIResponse? = null
)

@Serializable
data class SaleToPOIRequest (
    @SerialName("MessageHeader")
    val messageHeader: MessageHeader,

    @SerialName("SecurityTrailer")
    val securityTrailer: ContentInformationType? = null,

    @SerialName("AbortRequest")
    val abortRequest: AbortRequest? = null,

    @SerialName("AdminRequest")
    val adminRequest: AdminRequest? = null,

    @SerialName("BalanceInquiryRequest")
    val balanceInquiryRequest: BalanceInquiryRequest? = null,

    @SerialName("BatchRequest")
    val batchRequest: BatchRequest? = null,

    @SerialName("CardAcquisitionRequest")
    val cardAcquisitionRequest: CardAcquisitionRequest? = null,

    @SerialName("CardReaderAPDURequest")
    val cardReaderAPDURequest: List<CardReaderAPDURequest>? = null,

    @SerialName("CardReaderInitRequest")
    val cardReaderInitRequest: CardReaderInitRequest? = null,

    @SerialName("CardReaderPowerOffRequest")
    val cardReaderPowerOffRequest: CardReaderPowerOffRequest? = null,

    @SerialName("DiagnosisRequest")
    val diagnosisRequest: DiagnosisRequest? = null,

    @SerialName("DisplayRequest")
    val displayRequest: DisplayRequest? = null,

    @SerialName("EnableServiceRequest")
    val enableServiceRequest: EnableServiceRequest? = null,

    @SerialName("EventNotification")
    val eventNotification: EventNotification? = null,

    @SerialName("GetTotalsRequest")
    val getTotalsRequest: GetTotalsRequest? = null,

    @SerialName("InputRequest")
    val inputRequest: InputRequest? = null,

    @SerialName("InputUpdate")
    val inputUpdate: InputUpdate? = null,

    @SerialName("LoginRequest")
    val loginRequest: LoginRequest? = null,

    @SerialName("LogoutRequest")
    val logoutRequest: LogoutRequest? = null,

    @SerialName("LoyaltyRequest")
    val loyaltyRequest: LoyaltyRequest? = null,

    @SerialName("PaymentRequest")
    val paymentRequest: PaymentRequest? = null,

    @SerialName("PINRequest")
    val pinRequest: PINRequest? = null,

    @SerialName("PrintRequest")
    val printRequest: PrintRequest? = null,

    @SerialName("ReconciliationRequest")
    val reconciliationRequest: ReconciliationRequest? = null,

    @SerialName("ReversalRequest")
    val reversalRequest: ReversalRequest? = null,

    @SerialName("SoundRequest")
    val soundRequest: SoundRequest? = null,

    @SerialName("StoredValueRequest")
    val storedValueRequest: StoredValueRequest? = null,

    @SerialName("TransactionStatusRequest")
    val transactionStatusRequest: TransactionStatusRequest? = null,

    @SerialName("TransmitRequest")
    val transmitRequest: TransmitRequest? = null
)

/**
 * Content of the Abort Request message, used by the Sale System to halt and terminate the
 * processing of a message in progress.
 */
@Serializable
data class AbortRequest (
    /**
     * Free text reason for aborting the transaction, for logging purposes.
     */
    @SerialName("AbortReason")
    val abortReason: String,

    /**
     * Optional message to display to the Customer on the POI during the abort.
     */
    @SerialName("DisplayOutput")
    val displayOutput: DisplayOutput? = null,

    @SerialName("MessageReference")
    val messageReference: MessageReference
)

/**
 * Optional message to display to the Customer on the POI during the abort.
 *
 * A complete display operation for a logical device, including the content to display and
 * processing parameters.
 *
 * Optional prompt or welcome message to display on the CustomerDisplay of the POI Terminal.
 */
@Serializable
data class DisplayOutput (
    @SerialName("Device")
    val device: DeviceEnum,

    @SerialName("InfoQualify")
    val infoQualify: InfoQualifyEnum,

    @SerialName("MenuEntry")
    val menuEntry: List<MenuEntry>? = null,

    /**
     * Minimum number of seconds the message must remain displayed. Response is sent immediately
     * regardless.
     */
    @SerialName("MinimumDisplayTime")
    val minimumDisplayTime: Long? = null,

    @SerialName("OutputContent")
    val outputContent: OutputContent,

    /**
     * Vendor-specific signature protecting the text to display or print.
     */
    @SerialName("OutputSignature")
    val outputSignature: ContentInformationType? = null,

    /**
     * When true, the receiver must send a Display Response for this output. Default true.
     */
    @SerialName("ResponseRequiredFlag")
    val responseRequiredFlag: Boolean? = null
)

/**
 * Logical device on a Sale or POI Terminal: CashierDisplay, CustomerDisplay, CashierInput,
 * or CustomerInput.
 */
@Serializable
enum class DeviceEnum(val value: String) {
    @SerialName("CashierDisplay") CashierDisplay("CashierDisplay"),
    @SerialName("CashierInput") CashierInput("CashierInput"),
    @SerialName("CustomerDisplay") CustomerDisplay("CustomerDisplay"),
    @SerialName("CustomerInput") CustomerInput("CustomerInput");
}

/**
 * Qualification of information sent to an output device: Status (state change), Error
 * (error situation), Display (standard display), Sound, Input (entry requested),
 * POIReplication (mirror of POI customer display), CustomerAssistance (cashier assisting
 * customer), Receipt, Document, or Voucher.
 */
@Serializable
enum class InfoQualifyEnum(val value: String) {
    @SerialName("CustomerAssistance") CustomerAssistance("CustomerAssistance"),
    @SerialName("Display") Display("Display"),
    @SerialName("Document") Document("Document"),
    @SerialName("Error") Error("Error"),
    @SerialName("Input") Input("Input"),
    @SerialName("POIReplication") POIReplication("POIReplication"),
    @SerialName("Receipt") Receipt("Receipt"),
    @SerialName("Sound") Sound("Sound"),
    @SerialName("Status") Status("Status"),
    @SerialName("Voucher") Voucher("Voucher");
}

/**
 * A single entry in a menu presented to the user during a GetMenuEntry input command.
 */
@Serializable
data class MenuEntry (
    /**
     * When true, this entry is pre-selected before any user action. Default false.
     */
    @SerialName("DefaultSelectedFlag")
    val defaultSelectedFlag: Boolean? = null,

    /**
     * Characteristics of this menu entry (selectable, non-selectable, sub-menu). Default
     * Selectable.
     */
    @SerialName("MenuEntryTag")
    val menuEntryTag: MenuEntryTagEnum? = null,

    @SerialName("OutputFormat")
    val outputFormat: OutputFormatEnum,

    @SerialName("OutputText")
    val outputText: List<OutputText>? = null,

    /**
     * Base64-encoded XHTML content for this menu entry.
     */
    @SerialName("OutputXHTML")
    val outputXHTML: String? = null,

    @SerialName("PredefinedContent")
    val predefinedContent: PredefinedContent? = null
)

/**
 * Characteristics of this menu entry (selectable, non-selectable, sub-menu). Default
 * Selectable.
 *
 * Characteristics of a menu entry: Selectable, NonSelectable, SubMenu (selection shows
 * sub-menu), or NonSelectableSubMenu.
 */
@Serializable
enum class MenuEntryTagEnum(val value: String) {
    @SerialName("NonSelectable") NonSelectable("NonSelectable"),
    @SerialName("NonSelectableSubMenu") NonSelectableSubMenu("NonSelectableSubMenu"),
    @SerialName("Selectable") Selectable("Selectable"),
    @SerialName("SubMenu") SubMenu("SubMenu");
}

/**
 * Format of the content to display or print: MessageRef (predefined message), Text
 * (formatted text), XHTML (XHTML document), or BarCode.
 */
@Serializable
enum class OutputFormatEnum(val value: String) {
    @SerialName("BarCode") BarCode("BarCode"),
    @SerialName("MessageRef") MessageRef("MessageRef"),
    @SerialName("Text") Text("Text"),
    @SerialName("XHTML") XHTML("XHTML");
}

/**
 * Content of a formatted text message to display or print, including layout and style
 * parameters.
 */
@Serializable
data class OutputText (
    @SerialName("Alignment")
    val alignment: AlignmentEnum? = null,

    @SerialName("CharacterHeight")
    val characterHeight: CharacterHeightEnum? = null,

    /**
     * IANA character encoding number for the text (used for ASN.1 encoding; for XML the
     * document encoding is used).
     */
    @SerialName("CharacterSet")
    val characterSet: Long? = null,

    @SerialName("CharacterStyle")
    val characterStyle: CharacterStyleEnum? = null,

    @SerialName("CharacterWidth")
    val characterWidth: CharacterWidthEnum? = null,

    @SerialName("Color")
    val color: ColorEnum? = null,

    /**
     * When true, a newline and carriage return are appended after the text. Default true.
     */
    @SerialName("EndOfLineFlag")
    val endOfLineFlag: Boolean? = null,

    /**
     * Name of the font to use, as agreed between POI and Sale Systems.
     */
    @SerialName("Font")
    val font: String? = null,

    /**
     * Column position from which the text string is displayed or printed (1-based).
     */
    @SerialName("StartColumn")
    val startColumn: Long? = null,

    /**
     * Row position from which the text string is displayed or printed (1-based).
     */
    @SerialName("StartRow")
    val startRow: Long? = null,

    /**
     * Text content to display or print.
     */
    @SerialName("Text")
    val text: String
)

/**
 * Alignment of the text string on the display or print line.
 */
@Serializable
enum class AlignmentEnum(val value: String) {
    @SerialName("Centred") Centred("Centred"),
    @SerialName("Justified") Justified("Justified"),
    @SerialName("Left") Left("Left"),
    @SerialName("Right") Right("Right");
}

/**
 * Character height of the text string to display or print.
 */
@Serializable
enum class CharacterHeightEnum(val value: String) {
    @SerialName("DoubleHeight") DoubleHeight("DoubleHeight"),
    @SerialName("HalfHeight") HalfHeight("HalfHeight"),
    @SerialName("SingleHeight") SingleHeight("SingleHeight");
}

/**
 * Typographic style of the text to display or print.
 */
@Serializable
enum class CharacterStyleEnum(val value: String) {
    @SerialName("Bold") Bold("Bold"),
    @SerialName("Italic") Italic("Italic"),
    @SerialName("Normal") Normal("Normal"),
    @SerialName("Underlined") Underlined("Underlined");
}

/**
 * Character width of the text string to display or print.
 */
@Serializable
enum class CharacterWidthEnum(val value: String) {
    @SerialName("DoubleWidth") DoubleWidth("DoubleWidth"),
    @SerialName("SingleWidth") SingleWidth("SingleWidth");
}

/**
 * Color of the text string to display or print.
 */
@Serializable
enum class ColorEnum(val value: String) {
    @SerialName("Black") Black("Black"),
    @SerialName("Blue") Blue("Blue"),
    @SerialName("Cyan") Cyan("Cyan"),
    @SerialName("Green") Green("Green"),
    @SerialName("Magenta") Magenta("Magenta"),
    @SerialName("Red") Red("Red"),
    @SerialName("White") White("White"),
    @SerialName("Yellow") Yellow("Yellow");
}

/**
 * Reference to a predefined message stored on the receiving system, identified by
 * ReferenceID with an optional language.
 */
@Serializable
data class PredefinedContent (
    /**
     * Language of the predefined message to retrieve.
     */
    @SerialName("Language")
    val language: String? = null,

    /**
     * Identification of the predefined message to display, print, or play.
     */
    @SerialName("ReferenceID")
    val referenceID: String
)

/**
 * Content to display or print, in one of the supported formats: predefined message
 * reference, formatted text, XHTML, or barcode.
 */
@Serializable
data class OutputContent (
    @SerialName("OutputBarcode")
    val outputBarcode: OutputBarcode? = null,

    @SerialName("OutputFormat")
    val outputFormat: OutputFormatEnum,

    @SerialName("OutputText")
    val outputText: List<OutputText>? = null,

    /**
     * Base64-encoded XHTML document body containing the message to display or print.
     */
    @SerialName("OutputXHTML")
    val outputXHTML: String? = null,

    @SerialName("PredefinedContent")
    val predefinedContent: PredefinedContent? = null
)

/**
 * Barcode content to display or print.
 */
@Serializable
data class OutputBarcode (
    @SerialName("BarcodeType")
    val barcodeType: BarcodeTypeEnum? = null,

    /**
     * Value to encode in the barcode.
     */
    @SerialName("BarcodeValue")
    val barcodeValue: String
)

/**
 * Type of barcode encoding used for display or print.
 */
@Serializable
enum class BarcodeTypeEnum(val value: String) {
    @SerialName("Code128") Code128("Code128"),
    @SerialName("Code25") Code25("Code25"),
    @SerialName("EAN13") Ean13("EAN13"),
    @SerialName("EAN8") Ean8("EAN8"),
    @SerialName("PDF417") Pdf417("PDF417"),
    @SerialName("QRCode") QRCode("QRCode"),
    @SerialName("UPCA") Upca("UPCA");
}

/**
 * CMS generic data structure used to protect data by encryption, MAC, or digest.
 *
 * Vendor-specific signature protecting the text to display or print.
 *
 * CMS EnvelopedData containing the encrypted SensitiveCardData structure.
 *
 * CMS EnvelopedData containing the encrypted SensitiveMobileData structure.
 *
 * CMS-encrypted handwritten signature captured on the POI.
 *
 * CMS-protected password. Mandatory for Password command when encryption is used.
 */
@Serializable
data class ContentInformationType (
    @SerialName("AuthenticatedData")
    val authenticatedData: AuthenticatedData? = null,

    /**
     * Identifies the type of CMS protection applied to the content.
     */
    @SerialName("ContentType")
    val contentType: SecurityTrailerContentType,

    @SerialName("DigestedData")
    val digestedData: DigestedData? = null,

    @SerialName("EnvelopedData")
    val envelopedData: EnvelopedData? = null
)

/**
 * CMS AuthenticatedData — protects the whole message (header and body) with a Retail
 * CBC-MAC using SHA-256.
 */
@Serializable
data class AuthenticatedData (
    @SerialName("EncapsulatedContent")
    val encapsulatedContent: EncapsulatedContent,

    @SerialName("KEK")
    val kek: Kek,

    /**
     * Base64-encoded 8-byte Retail CBC-MAC computed over the SHA-256 digest of the concatenated
     * MessageHeader and MessageBody.
     */
    @SerialName("MAC")
    val mac: String,

    @SerialName("MACAlgorithm")
    val macAlgorithm: AlgorithmIdentifier,

    /**
     * Version of the AuthenticatedData structure, always 'v0'.
     */
    @SerialName("Version")
    val version: AuthenticatedDataVersion
)

/**
 * Identifies the content type of the encapsulated data within a CMS structure.
 */
@Serializable
data class EncapsulatedContent (
    /**
     * Content type, always 'id-data'.
     */
    @SerialName("ContentType")
    val contentType: EncapsulatedContentContentType,

    /**
     * Base64-encoded digest value used in DigestedData.
     */
    @SerialName("Digest")
    val digest: String? = null
)

/**
 * Content type, always 'id-data'.
 *
 * Content type of the encrypted data, always 'id-data'.
 */
@Serializable
enum class EncapsulatedContentContentType(val value: String) {
    @SerialName("id-data") IDData("id-data");
}

/**
 * Key Encryption Key structure — carries the session key encrypted by the KEK using
 * Triple-DES ECB.
 */
@Serializable
data class Kek (
    /**
     * Base64-encoded session key encrypted by the KEK using Triple-DES in CBC mode.
     */
    @SerialName("EncryptedKey")
    val encryptedKey: String,

    @SerialName("KEKIdentifier")
    val kekIdentifier: KEKIdentifier,

    @SerialName("KeyEncryptionAlgorithm")
    val keyEncryptionAlgorithm: AlgorithmIdentifier,

    /**
     * Version of the KEK structure, always 'v4'.
     */
    @SerialName("Version")
    val version: KEKVersion
)

/**
 * Identifies the Key Encryption Key (KEK) used to encrypt the session key.
 */
@Serializable
data class KEKIdentifier (
    /**
     * Name of the KEK. Contains the suffix 'MACKey' for MAC keys or 'DATKey' for data
     * encryption keys.
     */
    @SerialName("KeyIdentifier")
    val keyIdentifier: String,

    /**
     * Version of the KEK, typically representing the key creation date.
     */
    @SerialName("KeyVersion")
    val keyVersion: String? = null
)

/**
 * Identifies a cryptographic algorithm and its parameters.
 */
@Serializable
data class AlgorithmIdentifier (
    /**
     * Name of the cryptographic algorithm (e.g. 'des-ede3-cbc', 'id-retail-cbc-mac-sha-256').
     */
    @SerialName("Algorithm")
    val algorithm: String,

    /**
     * Optional parameters for the algorithm.
     */
    @SerialName("Parameter")
    val parameter: Parameter? = null
)

/**
 * Optional parameters for the algorithm.
 */
@Serializable
data class Parameter (
    /**
     * Base64-encoded 8-byte IV for CBC mode.
     */
    @SerialName("InitialisationVector")
    val initialisationVector: String? = null
)

/**
 * Version of the KEK structure, always 'v4'.
 */
@Serializable
enum class KEKVersion(val value: String) {
    @SerialName("v4") V4("v4");
}

/**
 * Version of the AuthenticatedData structure, always 'v0'.
 *
 * Version of the EnvelopedData structure, always 'v0'.
 */
@Serializable
enum class AuthenticatedDataVersion(val value: String) {
    @SerialName("v0") V0("v0");
}

/**
 * Identifies the type of CMS protection applied to the content.
 */
@Serializable
enum class SecurityTrailerContentType(val value: String) {
    @SerialName("id-ct-authData") IDCTAuthData("id-ct-authData"),
    @SerialName("id-data") IDData("id-data"),
    @SerialName("id-digestedData") IDDigestedData("id-digestedData"),
    @SerialName("id-envelopedData") IDEnvelopedData("id-envelopedData");
}

/**
 * CMS DigestedData — used for integrity protection via a digest of the content.
 */
@Serializable
data class DigestedData (
    /**
     * Base64-encoded digest value.
     */
    @SerialName("Digest")
    val digest: String? = null,

    @SerialName("DigestAlgorithm")
    val digestAlgorithm: AlgorithmIdentifier,

    @SerialName("EncapsulatedContent")
    val encapsulatedContent: EncapsulatedContent
)

/**
 * CMS EnvelopedData — protects sensitive data (e.g. card data) by encryption using a
 * session key transported by a KEK.
 */
@Serializable
data class EnvelopedData (
    @SerialName("EncryptedContent")
    val encryptedContent: EncryptedContent,

    @SerialName("KEK")
    val kek: Kek,

    /**
     * Version of the EnvelopedData structure, always 'v0'.
     */
    @SerialName("Version")
    val version: AuthenticatedDataVersion
)

/**
 * Contains the encrypted data and the algorithm used to encrypt it.
 */
@Serializable
data class EncryptedContent (
    @SerialName("ContentEncryptionAlgorithm")
    val contentEncryptionAlgorithm: AlgorithmIdentifier,

    /**
     * Content type of the encrypted data, always 'id-data'.
     */
    @SerialName("ContentType")
    val contentType: EncapsulatedContentContentType,

    /**
     * Base64-encoded result of the Triple-DES CBC encryption of the padded data.
     */
    @SerialName("EncryptedData")
    val encryptedData: String
)

/**
 * Reference to a previous message request, used in Abort and TransactionStatus requests to
 * identify the target transaction.
 */
@Serializable
data class MessageReference (
    /**
     * DeviceID copied from the original message request header.
     */
    @SerialName("DeviceID")
    val deviceID: String? = null,

    @SerialName("MessageCategory")
    val messageCategory: MessageCategoryType? = null,

    /**
     * Identification of the POI Terminal that received the original message. Default is
     * MessageHeader.POIID.
     */
    @SerialName("POIID")
    val poiid: String? = null,

    /**
     * Identification of the Sale Terminal that sent the original message. Default is
     * MessageHeader.SaleID.
     */
    @SerialName("SaleID")
    val saleID: String? = null,

    /**
     * ServiceID copied from the original message request header.
     */
    @SerialName("ServiceID")
    val serviceID: String? = null
)

/**
 * Category of message identifying the type of service or operation being requested or
 * responded to.
 */
@Serializable
enum class MessageCategoryType(val value: String) {
    @SerialName("Abort") Abort("Abort"),
    @SerialName("Admin") Admin("Admin"),
    @SerialName("BalanceInquiry") BalanceInquiry("BalanceInquiry"),
    @SerialName("Batch") Batch("Batch"),
    @SerialName("CardAcquisition") CardAcquisition("CardAcquisition"),
    @SerialName("CardReaderAPDU") CardReaderAPDU("CardReaderAPDU"),
    @SerialName("CardReaderInit") CardReaderInit("CardReaderInit"),
    @SerialName("CardReaderPowerOff") CardReaderPowerOff("CardReaderPowerOff"),
    @SerialName("Diagnosis") Diagnosis("Diagnosis"),
    @SerialName("Display") Display("Display"),
    @SerialName("EnableService") EnableService("EnableService"),
    @SerialName("Event") Event("Event"),
    @SerialName("GetTotals") GetTotals("GetTotals"),
    @SerialName("Input") Input("Input"),
    @SerialName("InputUpdate") InputUpdate("InputUpdate"),
    @SerialName("Login") Login("Login"),
    @SerialName("Logout") Logout("Logout"),
    @SerialName("Loyalty") Loyalty("Loyalty"),
    @SerialName("Payment") Payment("Payment"),
    @SerialName("PIN") Pin("PIN"),
    @SerialName("Print") Print("Print"),
    @SerialName("Reconciliation") Reconciliation("Reconciliation"),
    @SerialName("Reversal") Reversal("Reversal"),
    @SerialName("Sound") Sound("Sound"),
    @SerialName("StoredValue") StoredValue("StoredValue"),
    @SerialName("TransactionStatus") TransactionStatus("TransactionStatus"),
    @SerialName("Transmit") Transmit("Transmit");
}

/**
 * Content of the Admin Request message, used to select and start customised administrative
 * services on the POI.
 */
@Serializable
data class AdminRequest (
    /**
     * Direct identification of the administrative service to execute, bypassing the interactive
     * menu. May be a name or CSV path of menu items.
     */
    @SerialName("ServiceIdentification")
    val serviceIdentification: String? = null
)

/**
 * Content of the Balance Inquiry Request message, used to query the balance of a payment,
 * loyalty, or stored value account.
 */
@Serializable
data class BalanceInquiryRequest (
    @SerialName("LoyaltyAccountReq")
    val loyaltyAccountReq: LoyaltyAccountReq? = null,

    @SerialName("PaymentAccountReq")
    val paymentAccountReq: PaymentAccountReq? = null
)

/**
 * Data related to the loyalty account for which a balance inquiry is requested.
 */
@Serializable
data class LoyaltyAccountReq (
    @SerialName("CardAcquisitionReference")
    val cardAcquisitionReference: TransactionIdentificationType? = null,

    @SerialName("LoyaltyAccountID")
    val loyaltyAccountID: LoyaltyAccountID? = null
)

/**
 * Identification of a transaction for the Sale System or the POI System. The combination of
 * TransactionID and TimeStamp ensures uniqueness.
 *
 * Reference to a previous CardAcquisition transaction from which to reuse the loyalty
 * account identification.
 *
 * Reference to a previous CardAcquisition transaction to reuse its card data for this
 * payment.
 *
 * Unique identification of the transaction for the Sale System (e.g. ticket number).
 *
 * Identification of the transaction assigned by the Acquirer, when different from the
 * POITransactionID.
 *
 * Unique identification of the transaction assigned by the POI Terminal. Mandatory in all
 * response messages.
 */
@Serializable
data class TransactionIdentificationType (
    /**
     * Date and time of the transaction, used together with TransactionID to ensure uniqueness
     * and allow log correlation.
     */
    @SerialName("TimeStamp")
    val timeStamp: String,

    /**
     * Unique identification of a transaction (e.g. ticket number).
     */
    @SerialName("TransactionID")
    val transactionID: String
)

/**
 * Identification of a loyalty account, including how the identification was obtained.
 */
@Serializable
data class LoyaltyAccountID (
    @SerialName("EntryMode")
    val entryMode: List<EntryModeType>,

    @SerialName("IdentificationSupport")
    val identificationSupport: IdentificationSupportEnum? = null,

    @SerialName("IdentificationType")
    val identificationType: IdentificationTypeEnum,

    /**
     * Loyalty account identification conforming to the IdentificationType.
     */
    @SerialName("LoyaltyID")
    val loyaltyID: String
)

/**
 * Entry mode of the payment instrument information. In a request, informs the POI how the
 * payment instrument data was read by the Sale Terminal. In a response, informs the Sale
 * how the POI read it.
 */
@Serializable
enum class EntryModeType(val value: String) {
    @SerialName("Contactless") Contactless("Contactless"),
    @SerialName("File") File("File"),
    @SerialName("ICC") Icc("ICC"),
    @SerialName("Keyed") Keyed("Keyed"),
    @SerialName("MagStripe") MagStripe("MagStripe"),
    @SerialName("Manual") Manual("Manual"),
    @SerialName("Mobile") Mobile("Mobile"),
    @SerialName("RFID") RFID("RFID"),
    @SerialName("Scanned") Scanned("Scanned"),
    @SerialName("SynchronousICC") SynchronousICC("SynchronousICC"),
    @SerialName("Tapped") Tapped("Tapped");
}

/**
 * Support medium of the loyalty account identification: NoCard (not on a card), LoyaltyCard
 * (dedicated loyalty card), HybridCard (combined payment/loyalty), or LinkedCard
 * (implicitly linked to payment card).
 */
@Serializable
enum class IdentificationSupportEnum(val value: String) {
    @SerialName("HybridCard") HybridCard("HybridCard"),
    @SerialName("LinkedCard") LinkedCard("LinkedCard"),
    @SerialName("LoyaltyCard") LoyaltyCard("LoyaltyCard"),
    @SerialName("NoCard") NoCard("NoCard");
}

/**
 * Type of account identification used for loyalty or stored value: PAN, ISOTrack2, BarCode,
 * AccountNumber, or PhoneNumber.
 */
@Serializable
enum class IdentificationTypeEnum(val value: String) {
    @SerialName("AccountNumber") AccountNumber("AccountNumber"),
    @SerialName("BarCode") BarCode("BarCode"),
    @SerialName("ISOTrack2") ISOTrack2("ISOTrack2"),
    @SerialName("PAN") Pan("PAN"),
    @SerialName("PhoneNumber") PhoneNumber("PhoneNumber");
}

/**
 * Data related to the payment account for which a balance inquiry is requested.
 */
@Serializable
data class PaymentAccountReq (
    @SerialName("AccountType")
    val accountType: AccountTypeEnum? = null,

    @SerialName("CardAcquisitionReference")
    val cardAcquisitionReference: TransactionIdentificationType? = null,

    @SerialName("PaymentInstrumentData")
    val paymentInstrumentData: PaymentInstrumentData? = null
)

/**
 * Type of cardholder account to use for a balance inquiry transaction.
 */
@Serializable
enum class AccountTypeEnum(val value: String) {
    @SerialName("CardTotals") CardTotals("CardTotals"),
    @SerialName("Checking") Checking("Checking"),
    @SerialName("CreditCard") CreditCard("CreditCard"),
    @SerialName("Default") Default("Default"),
    @SerialName("EpurseCard") EpurseCard("EpurseCard"),
    @SerialName("Investment") Investment("Investment"),
    @SerialName("Savings") Savings("Savings"),
    @SerialName("Universal") Universal("Universal");
}

/**
 * Data related to the instrument of payment for the transaction (card, check, mobile,
 * stored value, or cash).
 */
@Serializable
data class PaymentInstrumentData (
    @SerialName("CardData")
    val cardData: CardData? = null,

    @SerialName("CheckData")
    val checkData: CheckData? = null,

    @SerialName("MobileData")
    val mobileData: MobileData? = null,

    @SerialName("PaymentInstrumentType")
    val paymentInstrumentType: PaymentInstrumentTypeEnum,

    @SerialName("StoredValueAccountID")
    val storedValueAccountID: StoredValueAccountID? = null
)

/**
 * Information related to the payment card used for the transaction, including
 * identification, entry mode, and optionally sensitive data.
 */
@Serializable
data class CardData (
    @SerialName("AllowedProduct")
    val allowedProduct: List<AllowedProduct>? = null,

    /**
     * Product codes payable by this card. Present when ErrorCondition is PaymentRestriction.
     */
    @SerialName("AllowedProductCode")
    val allowedProductCode: List<String>? = null,

    /**
     * 3-digit ISO 3166-1 country code attached to the card, used to determine local vs
     * international transactions.
     */
    @SerialName("CardCountryCode")
    val cardCountryCode: String? = null,

    @SerialName("EntryMode")
    val entryMode: List<EntryModeType>? = null,

    /**
     * Partially masked PAN with '*' characters, used when SensitiveCardData is protected by
     * ProtectedCardData.
     */
    @SerialName("MaskedPAN")
    val maskedPAN: String? = null,

    /**
     * Payment Account Reference (PAR) — identifies the PAN without being usable for a
     * transaction. Mandatory when available.
     */
    @SerialName("PaymentAccountRef")
    val paymentAccountRef: String? = null,

    /**
     * Type/brand of the payment card (e.g. VISA, Mastercard). Mandatory when PAN is readable.
     */
    @SerialName("PaymentBrand")
    val paymentBrand: String? = null,

    @SerialName("PaymentToken")
    val paymentToken: PaymentToken? = null,

    /**
     * CMS EnvelopedData containing the encrypted SensitiveCardData structure.
     */
    @SerialName("ProtectedCardData")
    val protectedCardData: ContentInformationType? = null,

    @SerialName("SensitiveCardData")
    val sensitiveCardData: SensitiveCardData? = null
)

/**
 * A product that is payable by the presented payment card, used when the card has product
 * restrictions.
 */
@Serializable
data class AllowedProduct (
    /**
     * Additional information related to the product.
     */
    @SerialName("AdditionalProductInfo")
    val additionalProductInfo: String? = null,

    /**
     * Standard EAN/UPC product code.
     */
    @SerialName("EanUpc")
    val eanUpc: String? = null,

    /**
     * Product code of a payable item.
     */
    @SerialName("ProductCode")
    val productCode: String,

    /**
     * Human-readable product name.
     */
    @SerialName("ProductLabel")
    val productLabel: String? = null
)

/**
 * Surrogate of the PAN used to identify a customer's payment mean without exposing the PAN.
 */
@Serializable
data class PaymentToken (
    /**
     * Date and time after which the token is no longer valid.
     */
    @SerialName("ExpiryDateTime")
    val expiryDateTime: String? = null,

    @SerialName("TokenRequestedType")
    val tokenRequestedType: TokenRequestedTypeEnum,

    /**
     * Value of the payment token replacing the PAN.
     */
    @SerialName("TokenValue")
    val tokenValue: String
)

/**
 * Type of payment token requested to replace the PAN: Transaction (valid for one
 * transaction) or Customer (valid for a longer period to identify the customer).
 */
@Serializable
enum class TokenRequestedTypeEnum(val value: String) {
    @SerialName("Customer") Customer("Customer"),
    @SerialName("Transaction") Transaction("Transaction");
}

/**
 * Sensitive payment card data that may be CMS-protected (replaced by ProtectedCardData when
 * encrypted).
 */
@Serializable
data class SensitiveCardData (
    /**
     * Card Sequence Number per EMV tag 5F34 — distinguishes cards with the same PAN.
     */
    @SerialName("CardSeqNumb")
    val cardSeqNumb: String? = null,

    /**
     * Date after which the card cannot be used. Format MMYY.
     */
    @SerialName("ExpiryDate")
    val expiryDate: String? = null,

    /**
     * Primary Account Number — identifies the customer account or relationship. 8 to 28 digits.
     */
    @SerialName("PAN")
    val pan: String? = null,

    @SerialName("TrackData")
    val trackData: List<TrackData>? = null
)

/**
 * Magnetic track or magnetic ink characters line from a card or bank check.
 */
@Serializable
data class TrackData (
    @SerialName("TrackFormat")
    val trackFormat: TrackFormatEnum? = null,

    /**
     * ISO track number (1, 2, or 3). Default 2.
     */
    @SerialName("TrackNumb")
    val trackNumb: Long? = null,

    /**
     * Content of the card track or MICR line.
     */
    @SerialName("TrackValue")
    val trackValue: String
)

/**
 * Format of a magnetic card track or MICR line.
 */
@Serializable
enum class TrackFormatEnum(val value: String) {
    @SerialName("AAMVA") Aamva("AAMVA"),
    @SerialName("CMC-7") Cmc7("CMC-7"),
    @SerialName("E-13B") E13B("E-13B"),
    @SerialName("ISO") ISO("ISO"),
    @SerialName("JIS-I") JisI("JIS-I"),
    @SerialName("JIS-II") JisIi("JIS-II");
}

/**
 * Information related to a paper check used as a payment instrument.
 */
@Serializable
data class CheckData (
    /**
     * Customer account number. Mandatory when TrackData is absent.
     */
    @SerialName("AccountNumber")
    val accountNumber: String? = null,

    /**
     * Identification of the bank. Mandatory when TrackData is absent.
     */
    @SerialName("BankID")
    val bankID: String? = null,

    /**
     * Check guarantee card number presented during the check tendering process.
     */
    @SerialName("CheckCardNumber")
    val checkCardNumber: String? = null,

    /**
     * Identification of the bank check. Mandatory when TrackData is absent.
     */
    @SerialName("CheckNumber")
    val checkNumber: String? = null,

    /**
     * Country of the bank check. Absent when it is the country of the Sale System.
     */
    @SerialName("Country")
    val country: String? = null,

    @SerialName("TrackData")
    val trackData: TrackData? = null,

    /**
     * Type of bank check. Default Personal.
     */
    @SerialName("TypeCode")
    val typeCode: TypeCode? = null
)

/**
 * Type of bank check. Default Personal.
 */
@Serializable
enum class TypeCode(val value: String) {
    @SerialName("Company") Company("Company"),
    @SerialName("Personal") Personal("Personal");
}

/**
 * Information related to the mobile phone used as a payment instrument for the transaction.
 */
@Serializable
data class MobileData (
    @SerialName("Geolocation")
    val geolocation: Geolocation? = null,

    /**
     * Masked MSISDN showing country/national destination code and end digits separated by '*'.
     */
    @SerialName("MaskedMSISDN")
    val maskedMSISDN: String? = null,

    /**
     * 3-digit code identifying the country of the mobile operator per ITU-T E.212.
     */
    @SerialName("MobileCountryCode")
    val mobileCountryCode: String? = null,

    /**
     * 2–3 digit code identifying the mobile operator within a country per ITU-T E.212.
     */
    @SerialName("MobileNetworkCode")
    val mobileNetworkCode: String? = null,

    /**
     * CMS EnvelopedData containing the encrypted SensitiveMobileData structure.
     */
    @SerialName("ProtectedMobileData")
    val protectedMobileData: ContentInformationType? = null,

    @SerialName("SensitiveMobileData")
    val sensitiveMobileData: SensitiveMobileData? = null
)

/**
 * Geographic location of a mobile phone, specified using geographic or UTM coordinates.
 */
@Serializable
data class Geolocation (
    @SerialName("GeographicCoordinates")
    val geographicCoordinates: GeographicCoordinates? = null,

    @SerialName("UTMCoordinates")
    val utmCoordinates: UTMCoordinates? = null
)

/**
 * Geographic location specified by latitude and longitude coordinates.
 */
@Serializable
data class GeographicCoordinates (
    /**
     * Angular distance north or south of the equator in degrees, minutes, and seconds, followed
     * by N or S.
     */
    @SerialName("Latitude")
    val latitude: String,

    /**
     * Angular distance east or west of the Greenwich meridian in degrees, minutes, and seconds,
     * followed by E or W.
     */
    @SerialName("Longitude")
    val longitude: String
)

/**
 * Location on Earth specified by the Universal Transverse Mercator coordinate system using
 * the WGS84 ellipsoid.
 */
@Serializable
data class UTMCoordinates (
    /**
     * X-coordinate (easting) in the UTM system.
     */
    @SerialName("UTMEastward")
    val utmEastward: String,

    /**
     * Y-coordinate (northing) in the UTM system.
     */
    @SerialName("UTMNorthward")
    val utmNorthward: String,

    /**
     * UTM grid zone combining longitude zone (1–60) and latitude band (C–X, excluding I and O).
     */
    @SerialName("UTMZone")
    val utmZone: String
)

/**
 * Sensitive mobile phone subscriber data that may be CMS-protected.
 */
@Serializable
data class SensitiveMobileData (
    /**
     * International Mobile Equipment Identity per ITU-T E.212 — unique number identifying the
     * mobile device.
     */
    @SerialName("IMEI")
    val imei: String? = null,

    /**
     * International Mobile Subscriber Identity per ITU-T E.212 — contains MCC, MNC, and MSIN.
     */
    @SerialName("IMSI")
    val imsi: String? = null,

    /**
     * Mobile Subscriber Integrated Service Digital Network number (mobile phone number of the
     * SIM card).
     */
    @SerialName("MSISDN")
    val msisdn: String
)

/**
 * Type of payment instrument used for the transaction.
 */
@Serializable
enum class PaymentInstrumentTypeEnum(val value: String) {
    @SerialName("Card") Card("Card"),
    @SerialName("Cash") Cash("Cash"),
    @SerialName("Check") Check("Check"),
    @SerialName("Mobile") Mobile("Mobile"),
    @SerialName("StoredValue") StoredValue("StoredValue");
}

/**
 * Identification of a stored value account or card.
 */
@Serializable
data class StoredValueAccountID (
    @SerialName("EntryMode")
    val entryMode: List<EntryModeType>,

    /**
     * Date after which the stored value account or card cannot be used. Format MMYY.
     */
    @SerialName("ExpiryDate")
    val expiryDate: String? = null,

    @SerialName("IdentificationType")
    val identificationType: IdentificationTypeEnum,

    /**
     * Name of the owner of the stored value account.
     */
    @SerialName("OwnerName")
    val ownerName: String? = null,

    @SerialName("StoredValueAccountType")
    val storedValueAccountType: StoredValueAccountTypeEnum,

    /**
     * Stored value account identification conforming to the IdentificationType.
     */
    @SerialName("StoredValueID")
    val storedValueID: String,

    /**
     * Identification of the stored value account provider/host when the product code is
     * insufficient to identify it.
     */
    @SerialName("StoredValueProvider")
    val storedValueProvider: String? = null
)

/**
 * Type of stored value account instrument: GiftCard, PhoneCard, or Other.
 */
@Serializable
enum class StoredValueAccountTypeEnum(val value: String) {
    @SerialName("GiftCard") GiftCard("GiftCard"),
    @SerialName("Other") Other("Other"),
    @SerialName("PhoneCard") PhoneCard("PhoneCard");
}

/**
 * Content of the Batch Request message, used to send transactions for later execution or to
 * retrieve results of transactions performed without the Sale System.
 */
@Serializable
data class BatchRequest (
    /**
     * When true, transactions not yet performed are removed from the POI. Default false.
     */
    @SerialName("RemoveAllFlag")
    val removeAllFlag: Boolean? = null,

    @SerialName("TransactionToPerform")
    val transactionToPerform: List<TransactionToPerform>? = null
)

/**
 * A single transaction to perform in the batch.
 */
@Serializable
data class TransactionToPerform (
    @SerialName("PaymentRequest")
    val paymentRequest: PaymentRequest? = null,

    @SerialName("LoyaltyRequest")
    val loyaltyRequest: LoyaltyRequest? = null,

    @SerialName("ReversalRequest")
    val reversalRequest: ReversalRequest? = null
)

/**
 * Content of the Loyalty Request message, conveying all information required to process a
 * standalone loyalty transaction.
 */
@Serializable
data class LoyaltyRequest (
    @SerialName("LoyaltyData")
    val loyaltyData: List<LoyaltyData>? = null,

    @SerialName("LoyaltyTransaction")
    val loyaltyTransaction: LoyaltyTransaction,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Data related to a loyalty account used with a payment or loyalty transaction, provided by
 * the Sale System.
 */
@Serializable
data class LoyaltyData (
    /**
     * Reference to a previous CardAcquisition transaction from which to reuse the loyalty
     * account identification.
     */
    @SerialName("CardAcquisitionReference")
    val cardAcquisitionReference: TransactionIdentificationType? = null,

    @SerialName("LoyaltyAccountID")
    val loyaltyAccountID: LoyaltyAccountID? = null,

    @SerialName("LoyaltyAmount")
    val loyaltyAmount: LoyaltyAmount? = null
)

/**
 * Amount associated with a loyalty transaction, expressed in points or a monetary value.
 */
@Serializable
data class LoyaltyAmount (
    @SerialName("AmountValue")
    val amountValue: Double,

    @SerialName("Currency")
    val currency: String? = null,

    @SerialName("LoyaltyUnit")
    val loyaltyUnit: LoyaltyUnitEnum? = null
)

/**
 * Unit of a loyalty amount: Point (numeric points) or Monetary (amount in a currency).
 */
@Serializable
enum class LoyaltyUnitEnum(val value: String) {
    @SerialName("Monetary") Monetary("Monetary"),
    @SerialName("Point") Point("Point");
}

/**
 * Data related to a loyalty transaction, including type, amount, and conditions.
 */
@Serializable
data class LoyaltyTransaction (
    @SerialName("Currency")
    val currency: String? = null,

    @SerialName("LoyaltyTransactionType")
    val loyaltyTransactionType: LoyaltyTransactionTypeEnum,

    @SerialName("OriginalPOITransaction")
    val originalPOITransaction: OriginalPOITransaction? = null,

    @SerialName("SaleItem")
    val saleItem: List<SaleItem>? = null,

    /**
     * Amount of the related payment transaction on which the loyalty transaction is based.
     */
    @SerialName("TotalAmount")
    val totalAmount: Double? = null,

    @SerialName("TransactionConditions")
    val transactionConditions: TransactionConditions? = null
)

/**
 * Type of loyalty transaction: award, rebate, redemption, or their respective refunds.
 */
@Serializable
enum class LoyaltyTransactionTypeEnum(val value: String) {
    @SerialName("Award") Award("Award"),
    @SerialName("AwardRefund") AwardRefund("AwardRefund"),
    @SerialName("Rebate") Rebate("Rebate"),
    @SerialName("RebateRefund") RebateRefund("RebateRefund"),
    @SerialName("Redemption") Redemption("Redemption"),
    @SerialName("RedemptionRefund") RedemptionRefund("RedemptionRefund");
}

/**
 * Reference to a previous POI transaction, used for reversals, completions, refunds, or to
 * reuse card data from a prior transaction.
 */
@Serializable
data class OriginalPOITransaction (
    /**
     * Acquirer used for the original transaction, when the POI is multi-acquirer.
     */
    @SerialName("AcquirerID")
    val acquirerID: String? = null,

    /**
     * Amount of the original transaction. Used in reversal when POITransactionID is absent.
     */
    @SerialName("AmountValue")
    val amountValue: Double? = null,

    /**
     * Approval code from the original transaction, used for voice authorisation referrals.
     */
    @SerialName("ApprovalCode")
    val approvalCode: String? = null,

    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    @SerialName("HostTransactionID")
    val hostTransactionID: TransactionIdentificationType? = null,

    /**
     * Identification of the POI Terminal that performed the original transaction. Required when
     * the original transaction was on a different POI Terminal.
     */
    @SerialName("POIID")
    val poiid: String? = null,

    @SerialName("POITransactionID")
    val poiTransactionID: TransactionIdentificationType? = null,

    /**
     * When true, the POI reuses the card data from the original transaction without reading the
     * card again. Default true.
     */
    @SerialName("ReuseCardDataFlag")
    val reuseCardDataFlag: Boolean? = null,

    /**
     * Identification of the Sale Terminal that performed the original transaction. Required
     * when the reversal is sent from a different Sale Terminal.
     */
    @SerialName("SaleID")
    val saleID: String? = null
)

/**
 * A sale item in the transaction basket, used for payment cards with product restrictions
 * or loyalty processing.
 */
@Serializable
data class SaleItem (
    /**
     * Additional information related to the line item.
     */
    @SerialName("AdditionalProductInfo")
    val additionalProductInfo: String? = null,

    /**
     * Standard EAN/UPC product code. If sent, the POI stores and forwards it to the host when
     * the protocol allows.
     */
    @SerialName("EanUpc")
    val eanUpc: String? = null,

    /**
     * Total amount for this line item (quantity × unit price).
     */
    @SerialName("ItemAmount")
    val itemAmount: Double,

    /**
     * Identification of the item within the transaction (0 to n).
     */
    @SerialName("ItemID")
    val itemID: Long,

    /**
     * Product code of the purchased item (1–20 digits).
     */
    @SerialName("ProductCode")
    val productCode: String,

    /**
     * Human-readable product name.
     */
    @SerialName("ProductLabel")
    val productLabel: String? = null,

    /**
     * Quantity of the product purchased. If sent, the POI stores and forwards it.
     */
    @SerialName("Quantity")
    val quantity: Double? = null,

    /**
     * Commercial or distribution channel associated with this line item.
     */
    @SerialName("SaleChannel")
    val saleChannel: String? = null,

    /**
     * Tax type code associated with this line item.
     */
    @SerialName("TaxCode")
    val taxCode: String? = null,

    @SerialName("UnitOfMeasure")
    val unitOfMeasure: UnitOfMeasureEnum? = null,

    /**
     * Price per unit of the product. Required when Quantity is present.
     */
    @SerialName("UnitPrice")
    val unitPrice: Double? = null
)

/**
 * Unit of measure for a sale item quantity.
 */
@Serializable
enum class UnitOfMeasureEnum(val value: String) {
    @SerialName("Case") Case("Case"),
    @SerialName("Centilitre") Centilitre("Centilitre"),
    @SerialName("Centimetre") Centimetre("Centimetre"),
    @SerialName("Foot") Foot("Foot"),
    @SerialName("Gram") Gram("Gram"),
    @SerialName("Inch") Inch("Inch"),
    @SerialName("Kilogram") Kilogram("Kilogram"),
    @SerialName("Kilometre") Kilometre("Kilometre"),
    @SerialName("Litre") Litre("Litre"),
    @SerialName("Meter") Meter("Meter"),
    @SerialName("Mile") Mile("Mile"),
    @SerialName("Other") Other("Other"),
    @SerialName("Ounce") Ounce("Ounce"),
    @SerialName("Pint") Pint("Pint"),
    @SerialName("Pound") Pound("Pound"),
    @SerialName("Quart") Quart("Quart"),
    @SerialName("UKGallon") UKGallon("UKGallon"),
    @SerialName("USGallon") USGallon("USGallon"),
    @SerialName("Yard") Yard("Yard");
}

/**
 * Conditions and restrictions on the payment or loyalty transaction requested by the Sale
 * System.
 */
@Serializable
data class TransactionConditions (
    /**
     * Preferred Acquirers for this transaction. The POI must use one of these if present.
     */
    @SerialName("AcquirerID")
    val acquirerID: List<String>? = null,

    /**
     * Loyalty brands allowed for this transaction. Restricts the loyalty brand if present.
     */
    @SerialName("AllowedLoyaltyBrand")
    val allowedLoyaltyBrand: List<String>? = null,

    /**
     * Payment card brands allowed for this transaction. Restricts the brand if present.
     */
    @SerialName("AllowedPaymentBrand")
    val allowedPaymentBrand: List<String>? = null,

    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    /**
     * When true, a debit transaction is preferred over a credit transaction. Default false.
     */
    @SerialName("DebitPreferredFlag")
    val debitPreferredFlag: Boolean? = null,

    @SerialName("ForceEntryMode")
    val forceEntryMode: List<ForceEntryModeType>? = null,

    /**
     * When true, forces the POI to go online for authorisation regardless of card rules.
     * Default false.
     */
    @SerialName("ForceOnlineFlag")
    val forceOnlineFlag: Boolean? = null,

    @SerialName("LoyaltyHandling")
    val loyaltyHandling: LoyaltyHandlingEnum? = null,

    /**
     * ISO 18245 Merchant Category Code when the Sale Terminal has multiple MCCs for different
     * goods/services.
     */
    @SerialName("MerchantCategoryCode")
    val merchantCategoryCode: String? = null
)

/**
 * Restricts which card reader entry modes the POI may use for this transaction. Useful to
 * avoid retry on a known out-of-order device. The order of values gives priority among
 * modes.
 */
@Serializable
enum class ForceEntryModeType(val value: String) {
    @SerialName("CheckReader") CheckReader("CheckReader"),
    @SerialName("Contactless") Contactless("Contactless"),
    @SerialName("File") File("File"),
    @SerialName("ICC") Icc("ICC"),
    @SerialName("Keyed") Keyed("Keyed"),
    @SerialName("MagStripe") MagStripe("MagStripe"),
    @SerialName("Manual") Manual("Manual"),
    @SerialName("RFID") RFID("RFID"),
    @SerialName("Scanned") Scanned("Scanned"),
    @SerialName("SynchronousICC") SynchronousICC("SynchronousICC"),
    @SerialName("Tapped") Tapped("Tapped");
}

/**
 * Type of loyalty processing requested by the Sale System: Forbidden (no loyalty),
 * Processed (already done), Allowed (optional), Proposed (POI asks customer), or Required
 * (mandatory).
 */
@Serializable
enum class LoyaltyHandlingEnum(val value: String) {
    @SerialName("Allowed") Allowed("Allowed"),
    @SerialName("Forbidden") Forbidden("Forbidden"),
    @SerialName("Processed") Processed("Processed"),
    @SerialName("Proposed") Proposed("Proposed"),
    @SerialName("Required") Required("Required");
}

/**
 * Data associated with the Sale System context for a transaction, including transaction
 * identification and terminal characteristics.
 *
 * Sale transaction identification. Present only when the transaction was generated by the
 * Sale System.
 */
@Serializable
data class SaleData (
    @SerialName("CustomerOrderReq")
    val customerOrderReq: List<CustomerOrderReqType>? = null,

    /**
     * Identification of the Cashier or Operator. Sent when different from the Login value.
     */
    @SerialName("OperatorID")
    val operatorID: String? = null,

    /**
     * Language of the Cashier. Sent when different from the Login value.
     */
    @SerialName("OperatorLanguage")
    val operatorLanguage: String? = null,

    /**
     * Global Sale identification for a sequence of related POI transactions, e.g. for
     * reservation or customer orders.
     */
    @SerialName("SaleReferenceID")
    val saleReferenceID: String? = null,

    @SerialName("SaleTerminalData")
    val saleTerminalData: SaleTerminalData? = null,

    /**
     * Sale information forwarded unchanged to the Acquirer.
     */
    @SerialName("SaleToAcquirerData")
    val saleToAcquirerData: String? = null,

    /**
     * Sale information forwarded unchanged to the Issuer via the Acquirer.
     */
    @SerialName("SaleToIssuerData")
    val saleToIssuerData: String? = null,

    /**
     * Sale information stored by the POI with the transaction but not interpreted.
     */
    @SerialName("SaleToPOIData")
    val saleToPOIData: String? = null,

    /**
     * Unique identification of the transaction for the Sale System (e.g. ticket number).
     */
    @SerialName("SaleTransactionID")
    val saleTransactionID: TransactionIdentificationType,

    /**
     * Shift number. Sent when different from the Login value.
     */
    @SerialName("ShiftNumber")
    val shiftNumber: String? = null,

    /**
     * Reference label to print on the bank statement.
     */
    @SerialName("StatementReference")
    val statementReference: String? = null,

    @SerialName("TokenRequestedType")
    val tokenRequestedType: TokenRequestedTypeEnum? = null
)

/**
 * Specifies which customer orders the POI should include in a response: Open orders, Closed
 * orders, or Both.
 */
@Serializable
enum class CustomerOrderReqType(val value: String) {
    @SerialName("Both") Both("Both"),
    @SerialName("Closed") Closed("Closed"),
    @SerialName("Open") Open("Open");
}

/**
 * Information about the Sale Terminal software and hardware characteristics, sent in Login
 * and updated in subsequent messages when devices change.
 */
@Serializable
data class SaleTerminalData (
    @SerialName("SaleCapabilities")
    val saleCapabilities: List<SaleCapabilitiesType>? = null,

    @SerialName("SaleProfile")
    val saleProfile: SaleProfile? = null,

    @SerialName("TerminalEnvironment")
    val terminalEnvironment: TerminalEnvironmentType? = null,

    /**
     * Identification of a group of transactions on a POI Terminal sharing the same Sale
     * features, used for reconciliation grouping.
     */
    @SerialName("TotalsGroupID")
    val totalsGroupID: String? = null
)

/**
 * Hardware capabilities of the Sale Terminal that the POI System is allowed to use. Sent in
 * the Login Request to identify available Sale Terminal devices. In subsequent messages,
 * updated when a device becomes unavailable.
 */
@Serializable
enum class SaleCapabilitiesType(val value: String) {
    @SerialName("CashierDisplay") CashierDisplay("CashierDisplay"),
    @SerialName("CashierError") CashierError("CashierError"),
    @SerialName("CashierInput") CashierInput("CashierInput"),
    @SerialName("CashierStatus") CashierStatus("CashierStatus"),
    @SerialName("CustomerAssistance") CustomerAssistance("CustomerAssistance"),
    @SerialName("CustomerDisplay") CustomerDisplay("CustomerDisplay"),
    @SerialName("CustomerError") CustomerError("CustomerError"),
    @SerialName("CustomerInput") CustomerInput("CustomerInput"),
    @SerialName("EMVContactless") EMVContactless("EMVContactless"),
    @SerialName("ICC") Icc("ICC"),
    @SerialName("MagStripe") MagStripe("MagStripe"),
    @SerialName("POIReplication") POIReplication("POIReplication"),
    @SerialName("PrinterDocument") PrinterDocument("PrinterDocument"),
    @SerialName("PrinterReceipt") PrinterReceipt("PrinterReceipt"),
    @SerialName("PrinterVoucher") PrinterVoucher("PrinterVoucher");
}

/**
 * Functional profile of the Sale Terminal, declaring the generic profile and optional
 * service profiles supported during the session.
 *
 * Functional profile of the POI Terminal for this session.
 */
@Serializable
data class SaleProfile (
    @SerialName("GenericProfile")
    val genericProfile: GenericProfileType? = null,

    @SerialName("ServiceProfiles")
    val serviceProfiles: List<ServiceProfilesType>? = null
)

/**
 * Functional profile of the Sale to POI protocol indicating the group of messages
 * implemented: Basic (minimum), Standard (adds device sharing), or Extended (complete
 * interface).
 */
@Serializable
enum class GenericProfileType(val value: String) {
    @SerialName("Basic") Basic("Basic"),
    @SerialName("Extended") Extended("Extended"),
    @SerialName("Standard") Standard("Standard");
}

/**
 * List of optional service profiles supported by the Sale or POI Terminal. Sent in Login
 * Request/Response to declare which additional services may be requested or provided during
 * the session.
 */
@Serializable
enum class ServiceProfilesType(val value: String) {
    @SerialName("Batch") Batch("Batch"),
    @SerialName("CardReader") CardReader("CardReader"),
    @SerialName("Communication") Communication("Communication"),
    @SerialName("Loyalty") Loyalty("Loyalty"),
    @SerialName("OneTimeRes") OneTimeRes("OneTimeRes"),
    @SerialName("PIN") Pin("PIN"),
    @SerialName("Reservation") Reservation("Reservation"),
    @SerialName("Sound") Sound("Sound"),
    @SerialName("StoredValue") StoredValue("StoredValue"),
    @SerialName("Synchro") Synchro("Synchro");
}

/**
 * Environment of the terminal: Attended (cashier present), SemiAttended (customer
 * self-service with optional cashier assistance), or Unattended (fully automated).
 */
@Serializable
enum class TerminalEnvironmentType(val value: String) {
    @SerialName("Attended") Attended("Attended"),
    @SerialName("SemiAttended") SemiAttended("SemiAttended"),
    @SerialName("Unattended") Unattended("Unattended");
}

/**
 * Content of the Payment Request message, conveying all information required to process a
 * payment transaction.
 */
@Serializable
data class PaymentRequest (
    /**
     * Loyalty cards to process with the payment transaction, read by the Sale Terminal.
     */
    @SerialName("LoyaltyData")
    val loyaltyData: List<LoyaltyData>? = null,

    @SerialName("PaymentData")
    val paymentData: PaymentData? = null,

    @SerialName("PaymentTransaction")
    val paymentTransaction: PaymentTransaction,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Data specific to the payment transaction (as opposed to the loyalty part), including
 * payment type, split payment, and card data.
 */
@Serializable
data class PaymentData (
    /**
     * Reference to a previous CardAcquisition transaction to reuse its card data for this
     * payment.
     */
    @SerialName("CardAcquisitionReference")
    val cardAcquisitionReference: TransactionIdentificationType? = null,

    @SerialName("CustomerOrder")
    val customerOrder: CustomerOrder? = null,

    @SerialName("Instalment")
    val instalment: Instalment? = null,

    @SerialName("PaymentInstrumentData")
    val paymentInstrumentData: PaymentInstrumentData? = null,

    @SerialName("PaymentType")
    val paymentType: PaymentTypeEnum? = null,

    /**
     * Requested validity date for a OneTimeReservation, FirstReservation, or UpdateReservation.
     */
    @SerialName("RequestedValidityDate")
    val requestedValidityDate: String? = null,

    /**
     * When true, this payment is part of a split payment where the Sale transaction total is
     * paid in multiple transactions. Default false.
     */
    @SerialName("SplitPaymentFlag")
    val splitPaymentFlag: Boolean? = null
)

/**
 * Customer order recorded in the POI System, used for multi-step or multi-channel sale
 * transactions such as click-and-collect.
 */
@Serializable
data class CustomerOrder (
    /**
     * Identification of the Sale entity currently processing this order, for synchronisation.
     */
    @SerialName("AccessedBy")
    val accessedBy: String? = null,

    /**
     * Unqualified additional information about the customer order.
     */
    @SerialName("AdditionalInformation")
    val additionalInformation: String? = null,

    @SerialName("Currency")
    val currency: String? = null,

    /**
     * Total amount of all completed transactions within this customer order.
     */
    @SerialName("CurrentAmount")
    val currentAmount: Double,

    /**
     * Additional optional identification of the customer order.
     */
    @SerialName("CustomerOrderID")
    val customerOrderID: String? = null,

    /**
     * Date and time when the customer order was closed. Present when OpenOrderState is false.
     */
    @SerialName("EndDate")
    val endDate: String? = null,

    /**
     * Forecasted total amount of the order, set by the Sale System.
     */
    @SerialName("ForecastedAmount")
    val forecastedAmount: Double,

    /**
     * When true, the order is still open and awaiting further operations. Default true.
     */
    @SerialName("OpenOrderState")
    val openOrderState: Boolean? = null,

    /**
     * Sale System reference identifying this customer order.
     */
    @SerialName("SaleReferenceId")
    val saleReferenceID: String,

    /**
     * Date and time when the customer order was created.
     */
    @SerialName("StartDate")
    val startDate: String
)

/**
 * Information related to an instalment payment plan, either merchant-managed or
 * issuer-managed.
 */
@Serializable
data class Instalment (
    /**
     * Charges related to the instalment plan.
     */
    @SerialName("Charges")
    val charges: Double? = null,

    /**
     * Total cumulative amount of all instalments.
     */
    @SerialName("CumulativeAmount")
    val cumulativeAmount: Double? = null,

    /**
     * Amount of the first instalment when different from the others. Mandatory for
     * InequalInstalments.
     */
    @SerialName("FirstAmount")
    val firstAmount: Double? = null,

    /**
     * Date of the first instalment payment. Mandatory for DeferredInstalments.
     */
    @SerialName("FirstPaymentDate")
    val firstPaymentDate: String? = null,

    @SerialName("InstalmentType")
    val instalmentType: InstalmentTypeEnum? = null,

    /**
     * Number of PeriodUnit intervals between consecutive instalment payments.
     */
    @SerialName("Period")
    val period: Long? = null,

    @SerialName("PeriodUnit")
    val periodUnit: PeriodUnitEnum? = null,

    /**
     * Identification of the instalment plan.
     */
    @SerialName("PlanID")
    val planID: String? = null,

    /**
     * Sequence number of this instalment payment (1 to TotalNbOfPayments).
     */
    @SerialName("SequenceNumber")
    val sequenceNumber: Long? = null,

    /**
     * Total number of instalment payments including the first one.
     */
    @SerialName("TotalNbOfPayments")
    val totalNbOfPayments: Long? = null
)

/**
 * Type of instalment payment plan: DeferredInstalments, EqualInstalments, or
 * InequalInstalments.
 */
@Serializable
enum class InstalmentTypeEnum(val value: String) {
    @SerialName("DeferredInstalments") DeferredInstalments("DeferredInstalments"),
    @SerialName("EqualInstalments") EqualInstalments("EqualInstalments"),
    @SerialName("InequalInstalments") InequalInstalments("InequalInstalments");
}

/**
 * Unit of the period between consecutive instalment payments.
 */
@Serializable
enum class PeriodUnitEnum(val value: String) {
    @SerialName("Annual") Annual("Annual"),
    @SerialName("Daily") Daily("Daily"),
    @SerialName("Monthly") Monthly("Monthly"),
    @SerialName("Weekly") Weekly("Weekly");
}

/**
 * Type of payment transaction identifying the specific payment service requested.
 *
 * Type of payment service, mandatory for contactless card processing.
 */
@Serializable
enum class PaymentTypeEnum(val value: String) {
    @SerialName("CashAdvance") CashAdvance("CashAdvance"),
    @SerialName("CashDeposit") CashDeposit("CashDeposit"),
    @SerialName("Completion") Completion("Completion"),
    @SerialName("FirstReservation") FirstReservation("FirstReservation"),
    @SerialName("Instalment") Instalment("Instalment"),
    @SerialName("IssuerInstalment") IssuerInstalment("IssuerInstalment"),
    @SerialName("Normal") Normal("Normal"),
    @SerialName("OneTimeReservation") OneTimeReservation("OneTimeReservation"),
    @SerialName("PaidOut") PaidOut("PaidOut"),
    @SerialName("Recurring") Recurring("Recurring"),
    @SerialName("Refund") Refund("Refund"),
    @SerialName("UpdateReservation") UpdateReservation("UpdateReservation");
}

/**
 * Data related to the payment and loyalty transaction that are global to both (amounts,
 * conditions, sold items).
 */
@Serializable
data class PaymentTransaction (
    @SerialName("AmountsReq")
    val amountsReq: AmountsReq,

    @SerialName("OriginalPOITransaction")
    val originalPOITransaction: OriginalPOITransaction? = null,

    @SerialName("SaleItem")
    val saleItem: List<SaleItem>? = null,

    @SerialName("TransactionConditions")
    val transactionConditions: TransactionConditions? = null
)

/**
 * Various amounts requested by the Sale System for the payment transaction.
 */
@Serializable
data class AmountsReq (
    /**
     * Requested cashback amount as part of the payment. The POI must perform the cashback.
     */
    @SerialName("CashBackAmount")
    val cashBackAmount: Double? = null,

    @SerialName("Currency")
    val currency: String,

    /**
     * Maximum cashback amount the merchant allows for this transaction.
     */
    @SerialName("MaximumCashBackAmount")
    val maximumCashBackAmount: Double? = null,

    /**
     * Minimum amount the Sale System allows to deliver, used for OneTimeReservation when the
     * maximum is unknown.
     */
    @SerialName("MinimumAmountToDeliver")
    val minimumAmountToDeliver: Double? = null,

    /**
     * Minimum amount per split payment transaction, used to limit the number of splits.
     */
    @SerialName("MinimumSplitAmount")
    val minimumSplitAmount: Double? = null,

    /**
     * Amount already paid in previous split payment transactions for this Sale transaction.
     */
    @SerialName("PaidAmount")
    val paidAmount: Double? = null,

    /**
     * Total amount requested for payment, including cashback and tip. Must be greater than 0.
     */
    @SerialName("RequestedAmount")
    val requestedAmount: Double? = null,

    /**
     * Proposed tip amount. The POI asks the customer to validate or modify it.
     */
    @SerialName("TipAmount")
    val tipAmount: Double? = null
)

/**
 * Content of the Reversal Request message, used to cancel a previously completed payment or
 * loyalty transaction.
 */
@Serializable
data class ReversalRequest (
    @SerialName("CustomerOrder")
    val customerOrder: CustomerOrder? = null,

    @SerialName("OriginalPOITransaction")
    val originalPOITransaction: OriginalPOITransaction,

    @SerialName("ReversalReason")
    val reversalReason: ReversalReasonEnum,

    /**
     * Amount to reverse for a partial reversal. Implicitly equals the AuthorizedAmount of the
     * original transaction when absent.
     */
    @SerialName("ReversedAmount")
    val reversedAmount: Double? = null,

    @SerialName("SaleData")
    val saleData: SaleData? = null,

    /**
     * Sale reference identifying the reservation transaction to cancel. Mandatory for
     * reservation reversals.
     */
    @SerialName("SaleReferenceID")
    val saleReferenceID: String? = null
)

/**
 * Reason for reversing a payment or loyalty transaction.
 */
@Serializable
enum class ReversalReasonEnum(val value: String) {
    @SerialName("CustCancel") CustCancel("CustCancel"),
    @SerialName("Malfunction") Malfunction("Malfunction"),
    @SerialName("MerchantCancel") MerchantCancel("MerchantCancel"),
    @SerialName("Unable2Compl") Unable2Compl("Unable2Compl");
}

/**
 * Content of the Card Acquisition Request message, used to read and analyse payment/loyalty
 * cards before a transaction.
 */
@Serializable
data class CardAcquisitionRequest (
    @SerialName("CardAcquisitionTransaction")
    val cardAcquisitionTransaction: CardAcquisitionTransaction,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Data related to the card acquisition transaction, defining conditions and restrictions
 * for card reading.
 */
@Serializable
data class CardAcquisitionTransaction (
    /**
     * Loyalty brands allowed for this card acquisition.
     */
    @SerialName("AllowedLoyaltyBrand")
    val allowedLoyaltyBrand: List<String>? = null,

    /**
     * Payment brands allowed for this card acquisition.
     */
    @SerialName("AllowedPaymentBrand")
    val allowedPaymentBrand: List<String>? = null,

    /**
     * For contactless cards, true if cash back was requested. Default false.
     */
    @SerialName("CashBackFlag")
    val cashBackFlag: Boolean? = null,

    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    /**
     * When true, the customer must select the card application on a multi-application
     * smartcard. Default false.
     */
    @SerialName("ForceCustomerSelectionFlag")
    val forceCustomerSelectionFlag: Boolean? = null,

    @SerialName("ForceEntryMode")
    val forceEntryMode: List<ForceEntryModeType>? = null,

    @SerialName("LoyaltyHandling")
    val loyaltyHandling: LoyaltyHandlingEnum? = null,

    /**
     * Type of payment service, mandatory for contactless card processing.
     */
    @SerialName("PaymentType")
    val paymentType: PaymentTypeEnum? = null,

    /**
     * Transaction amount, mandatory for contactless card processing.
     */
    @SerialName("TotalAmount")
    val totalAmount: Double? = null
)

/**
 * Content of a single APDU command to exchange with an initialised smart card per ISO 7816.
 */
@Serializable
data class CardReaderAPDURequest (
    /**
     * Class field (CLA) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @SerialName("APDUClass")
    val apduClass: String,

    /**
     * Data field (Lc + Data) of the APDU command. Mandatory when the instruction requires data.
     */
    @SerialName("APDUData")
    val apduData: String? = null,

    /**
     * Expected length (Le) of the data in the APDU response. Base64-encoded 1 byte. Absent
     * means maximum available bytes are requested.
     */
    @SerialName("APDUExpectedLength")
    val apduExpectedLength: String? = null,

    /**
     * Instruction field (INS) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @SerialName("APDUInstruction")
    val apduInstruction: String,

    /**
     * Parameter 1 field (P1) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @SerialName("APDUPar1")
    val apduPar1: String,

    /**
     * Parameter 2 field (P2) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @SerialName("APDUPar2")
    val apduPar2: String
)

/**
 * Content of the Card Reader Init Request message, used to enable card insertion and
 * initialise a card in the POI card reader.
 */
@Serializable
data class CardReaderInitRequest (
    @SerialName("DisplayOutput")
    val displayOutput: DisplayOutput? = null,

    @SerialName("ForceEntryMode")
    val forceEntryMode: List<ForceEntryModeType>? = null,

    /**
     * When true, keeps the card in the reader after magnetic stripe reading to allow subsequent
     * chip dialogue. Default true.
     */
    @SerialName("LeaveCardFlag")
    val leaveCardFlag: Boolean? = null,

    /**
     * Maximum time in seconds to wait for the card to be inserted.
     */
    @SerialName("MaxWaitingTime")
    val maxWaitingTime: Long? = null,

    /**
     * When true, performs a warm reset on an already-initialised chip card. Default false.
     */
    @SerialName("WarmResetFlag")
    val warmResetFlag: Boolean? = null
)

/**
 * Content of the Card Reader Power-Off Request message, used to power off a smart card chip
 * and request the customer to remove the card.
 */
@Serializable
data class CardReaderPowerOffRequest (
    @SerialName("DisplayOutput")
    val displayOutput: DisplayOutput? = null,

    /**
     * Maximum time in seconds to wait for the card to be removed.
     */
    @SerialName("MaxWaitingTime")
    val maxWaitingTime: Long? = null
)

/**
 * Content of the Diagnosis Request message, used to request the operational status of a POI
 * Terminal and its components.
 */
@Serializable
data class DiagnosisRequest (
    /**
     * Specific Acquirer hosts to diagnose. All connected hosts are checked when absent and
     * HostDiagnosisFlag is true.
     */
    @SerialName("AcquirerID")
    val acquirerID: List<String>? = null,

    /**
     * When true, the POI also checks the reachability of all connected Acquirer hosts. Default
     * false.
     */
    @SerialName("HostDiagnosisFlag")
    val hostDiagnosisFlag: Boolean? = null,

    /**
     * Identification of the POI Terminal to diagnose. Default is MessageHeader.POIID.
     */
    @SerialName("POIID")
    val poiid: String? = null
)

/**
 * Content of the Display Request message, conveying one or more display commands for output
 * devices.
 */
@Serializable
data class DisplayRequest (
    @SerialName("DisplayOutput")
    val displayOutput: List<DisplayOutput>
)

/**
 * Content of the Enable Service Request message, used to enable swipe-ahead transactions or
 * abort a previously started one.
 */
@Serializable
data class EnableServiceRequest (
    /**
     * Optional prompt or welcome message to display on the CustomerDisplay of the POI Terminal.
     */
    @SerialName("DisplayOutput")
    val displayOutput: DisplayOutput? = null,

    /**
     * Financial services enabled for swipe-ahead. Mandatory when TransactionAction is
     * StartTransaction.
     */
    @SerialName("ServicesEnabled")
    val servicesEnabled: List<ServicesEnabledType>? = null,

    @SerialName("TransactionAction")
    val transactionAction: TransactionActionEnum
)

/**
 * Financial services enabled for swipe-ahead. Mandatory when TransactionAction is
 * StartTransaction.
 *
 * Financial services enabled by an EnableService request, allowing the POI to start one of
 * these services via the swipe-ahead mechanism before the Sale System sends the
 * corresponding service request.
 */
@Serializable
enum class ServicesEnabledType(val value: String) {
    @SerialName("CardAcquisition") CardAcquisition("CardAcquisition"),
    @SerialName("Loyalty") Loyalty("Loyalty"),
    @SerialName("Payment") Payment("Payment");
}

/**
 * Action to perform on a transaction via EnableService: StartTransaction (enable
 * swipe-ahead) or AbortTransaction (cancel a started swipe-ahead or CardAcquisition).
 */
@Serializable
enum class TransactionActionEnum(val value: String) {
    @SerialName("AbortTransaction") AbortTransaction("AbortTransaction"),
    @SerialName("StartTransaction") StartTransaction("StartTransaction");
}

/**
 * Content of the Event Notification message, sent by the POI to inform the Sale System of
 * an unsolicited event.
 */
@Serializable
data class EventNotification (
    /**
     * New language selected by the customer. Mandatory when EventToNotify is CustomerLanguage.
     */
    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    @SerialName("DisplayOutput")
    val displayOutput: List<DisplayOutput>? = null,

    /**
     * Additional information about the event for logging. Mandatory for SaleWakeUp (transaction
     * reference), KeyPressed (key ID), and SaleAdmin (service name).
     */
    @SerialName("EventDetails")
    val eventDetails: String? = null,

    @SerialName("EventToNotify")
    val eventToNotify: EventToNotifyEnum,

    /**
     * When true, the event requires a maintenance call or action. Default false.
     */
    @SerialName("MaintenanceRequiredFlag")
    val maintenanceRequiredFlag: Boolean? = null,

    /**
     * Base64-encoded content of the rejected message. Mandatory when EventToNotify is Reject.
     */
    @SerialName("RejectedMessage")
    val rejectedMessage: String? = null,

    @SerialName("TimeStamp")
    val timeStamp: String
)

/**
 * Type of unsolicited event the POI notifies to the Sale System.
 */
@Serializable
enum class EventToNotifyEnum(val value: String) {
    @SerialName("Abort") Abort("Abort"),
    @SerialName("BeginMaintenance") BeginMaintenance("BeginMaintenance"),
    @SerialName("CardInserted") CardInserted("CardInserted"),
    @SerialName("CardRemoved") CardRemoved("CardRemoved"),
    @SerialName("Completed") Completed("Completed"),
    @SerialName("CustomerLanguage") CustomerLanguage("CustomerLanguage"),
    @SerialName("EndMaintenance") EndMaintenance("EndMaintenance"),
    @SerialName("Initialised") Initialised("Initialised"),
    @SerialName("KeyPressed") KeyPressed("KeyPressed"),
    @SerialName("OutOfOrder") OutOfOrder("OutOfOrder"),
    @SerialName("Reject") Reject("Reject"),
    @SerialName("SaleAdmin") SaleAdmin("SaleAdmin"),
    @SerialName("SaleWakeUp") SaleWakeUp("SaleWakeUp"),
    @SerialName("SecurityAlarm") SecurityAlarm("SecurityAlarm"),
    @SerialName("Shutdown") Shutdown("Shutdown"),
    @SerialName("StopAssistance") StopAssistance("StopAssistance");
}

/**
 * Content of the GetTotals Request message, used to retrieve current period transaction
 * totals without closing the reconciliation period.
 */
@Serializable
data class GetTotalsRequest (
    @SerialName("TotalDetails")
    val totalDetails: List<TotalDetailsType>? = null,

    @SerialName("TotalFilter")
    val totalFilter: TotalFilter? = null
)

/**
 * Indicates the criteria by which transaction totals should be broken down in a GetTotals
 * response. Each value in the cluster requests a separate grouping dimension.
 */
@Serializable
enum class TotalDetailsType(val value: String) {
    @SerialName("OperatorID") OperatorID("OperatorID"),
    @SerialName("POIID") Poiid("POIID"),
    @SerialName("SaleID") SaleID("SaleID"),
    @SerialName("ShiftNumber") ShiftNumber("ShiftNumber"),
    @SerialName("TotalsGroupID") TotalsGroupID("TotalsGroupID");
}

/**
 * Filter criteria for GetTotals, restricting the totals to a specific POI Terminal, Sale
 * Terminal, Cashier, Shift, or Sale group.
 */
@Serializable
data class TotalFilter (
    /**
     * Filter totals to this specific Cashier/Operator only.
     */
    @SerialName("OperatorID")
    val operatorID: String? = null,

    /**
     * Filter totals to this specific POI Terminal only.
     */
    @SerialName("POIID")
    val poiid: String? = null,

    /**
     * Filter totals to this specific Sale Terminal only.
     */
    @SerialName("SaleID")
    val saleID: String? = null,

    /**
     * Filter totals to this specific shift only.
     */
    @SerialName("ShiftNumber")
    val shiftNumber: String? = null,

    /**
     * Filter totals to this specific Sale group only.
     */
    @SerialName("TotalsGroupID")
    val totalsGroupID: String? = null
)

/**
 * Content of the Input Request message, used to request information from the Cashier or
 * Customer through a display and input device.
 */
@Serializable
data class InputRequest (
    @SerialName("DisplayOutput")
    val displayOutput: DisplayOutput? = null,

    @SerialName("InputData")
    val inputData: InputData
)

/**
 * Parameters for an input command, defining the target device, type of input, and
 * constraints.
 */
@Serializable
data class InputData (
    /**
     * When true, a beep is generated each time the user presses a key. Default false.
     */
    @SerialName("BeepKeyFlag")
    val beepKeyFlag: Boolean? = null,

    /**
     * Default string pre-filled in the input field. For GetConfirmation: 'Y' or 'N'.
     */
    @SerialName("DefaultInputString")
    val defaultInputString: String? = null,

    @SerialName("Device")
    val device: DeviceEnum,

    /**
     * When true, the Cancel function key is disabled and not shown. Default false.
     */
    @SerialName("DisableCancelFlag")
    val disableCancelFlag: Boolean? = null,

    /**
     * When true, the Correct function key is disabled and not shown. Default false.
     */
    @SerialName("DisableCorrectFlag")
    val disableCorrectFlag: Boolean? = null,

    /**
     * When true, the Valid function key is disabled and not shown. Default false.
     */
    @SerialName("DisableValidFlag")
    val disableValidFlag: Boolean? = null,

    /**
     * When true, entered characters are displayed right-to-left (e.g. for amount entry).
     * Default false.
     */
    @SerialName("FromRightToLeftFlag")
    val fromRightToLeftFlag: Boolean? = null,

    /**
     * When true, pressing Correct clears all entered characters; when false, only the last
     * character is removed. Default false.
     */
    @SerialName("GlobalCorrectionFlag")
    val globalCorrectionFlag: Boolean? = null,

    /**
     * For GetAnyKey: when true, response is sent immediately without waiting for user
     * confirmation. Default true.
     */
    @SerialName("ImmediateResponseFlag")
    val immediateResponseFlag: Boolean? = null,

    @SerialName("InfoQualify")
    val infoQualify: InfoQualifyEnum,

    @SerialName("InputCommand")
    val inputCommand: InputCommandEnum,

    /**
     * When true, entered characters are masked (replaced by '•') in the display. Default false.
     */
    @SerialName("MaskCharactersFlag")
    val maskCharactersFlag: Boolean? = null,

    /**
     * Maximum number of digits after the decimal point for DecimalString input. Must be between
     * MinLength and MaxLength.
     */
    @SerialName("MaxDecimalLength")
    val maxDecimalLength: Long? = null,

    /**
     * Maximum time in seconds to wait for the user to complete the input before automatic
     * cancellation.
     */
    @SerialName("MaxInputTime")
    val maxInputTime: Long? = null,

    /**
     * Maximum length of the entered string, or maximum number of menu entries to select.
     */
    @SerialName("MaxLength")
    val maxLength: Long? = null,

    /**
     * For GetMenuEntry: when true, enables Back (returns -1) and Home (returns 0) navigation
     * keys. Default false.
     */
    @SerialName("MenuBackFlag")
    val menuBackFlag: Boolean? = null,

    /**
     * Minimum length of the entered string, or minimum number of menu entries to select.
     */
    @SerialName("MinLength")
    val minLength: Long? = null,

    /**
     * When true, the POI sends an InsertedCard error response if the customer inserts a card
     * instead of completing the input. Default false.
     */
    @SerialName("NotifyCardInputFlag")
    val notifyCardInputFlag: Boolean? = null,

    /**
     * Format mask for the input. Characters: 'd' (digit), 'a' (alpha), 's' (other printable),
     * any other char displayed but not entered, '\' escapes d/a/s/\.
     */
    @SerialName("StringMask")
    val stringMask: String? = null,

    /**
     * When true, waits for user confirmation even after reaching MaxLength, allowing
     * corrections. Default false.
     */
    @SerialName("WaitUserValidationFlag")
    val waitUserValidationFlag: Boolean? = null
)

/**
 * Type of input requested from the user: GetAnyKey (read confirmation), GetConfirmation
 * (yes/no), SiteManager (site manager confirmation), TextString, DigitString,
 * DecimalString, GetFunctionKey, GetMenuEntry, or Password.
 */
@Serializable
enum class InputCommandEnum(val value: String) {
    @SerialName("DecimalString") DecimalString("DecimalString"),
    @SerialName("DigitString") DigitString("DigitString"),
    @SerialName("GetAnyKey") GetAnyKey("GetAnyKey"),
    @SerialName("GetConfirmation") GetConfirmation("GetConfirmation"),
    @SerialName("GetFunctionKey") GetFunctionKey("GetFunctionKey"),
    @SerialName("GetMenuEntry") GetMenuEntry("GetMenuEntry"),
    @SerialName("Password") Password("Password"),
    @SerialName("SiteManager") SiteManager("SiteManager"),
    @SerialName("TextString") TextString("TextString");
}

/**
 * Content of the Input Update message, used to update the display of an Input request in
 * progress when an event requires a change.
 */
@Serializable
data class InputUpdate (
    /**
     * Updated maximum decimal length. Must be present if it was in the original Input request.
     */
    @SerialName("MaxDecimalLength")
    val maxDecimalLength: Long? = null,

    /**
     * Updated maximum input length. Must be present if it was in the original Input request.
     */
    @SerialName("MaxLength")
    val maxLength: Long? = null,

    @SerialName("MenuEntry")
    val menuEntry: List<MenuEntry>? = null,

    @SerialName("MessageReference")
    val messageReference: MessageReference,

    /**
     * Updated minimum input length. Must be present if it was in the original Input request.
     */
    @SerialName("MinLength")
    val minLength: Long? = null,

    @SerialName("OutputContent")
    val outputContent: OutputContent,

    @SerialName("OutputSignature")
    val outputSignature: ContentInformationType? = null
)

/**
 * Content of the Login Request message, conveying Sale System identification, terminal
 * characteristics, and session defaults.
 */
@Serializable
data class LoginRequest (
    @SerialName("CustomerOrderReq")
    val customerOrderReq: List<CustomerOrderReqType>? = null,

    /**
     * Date and time of the Sale System or Sale Terminal, allowing the POI to synchronise its
     * clock.
     */
    @SerialName("DateTime")
    val dateTime: String,

    /**
     * Identification of the Cashier driving the Sale Terminal. Sent for logging,
     * reconciliation, or acquirer requirements.
     */
    @SerialName("OperatorID")
    val operatorID: String? = null,

    /**
     * Default Cashier language for device displays during this session.
     */
    @SerialName("OperatorLanguage")
    val operatorLanguage: String,

    /**
     * Serial number of the POI Terminal as received in the last Login Response, to detect
     * hardware changes.
     */
    @SerialName("POISerialNumber")
    val poiSerialNumber: String? = null,

    @SerialName("SaleSoftware")
    val saleSoftware: SaleSoftware,

    @SerialName("SaleTerminalData")
    val saleTerminalData: SaleTerminalData? = null,

    /**
     * Shift number for the session. Sent for logging and reconciliation purposes.
     */
    @SerialName("ShiftNumber")
    val shiftNumber: String? = null,

    @SerialName("TokenRequestedType")
    val tokenRequestedType: TokenRequestedTypeEnum? = null,

    /**
     * When true, the entire session is for training/testing and no real transactions are
     * processed. Default false.
     */
    @SerialName("TrainingModeFlag")
    val trainingModeFlag: Boolean? = null
)

/**
 * Information identifying the Sale System software product that manages the Sale to POI
 * protocol.
 */
@Serializable
data class SaleSoftware (
    /**
     * Name of the Sale System software application.
     */
    @SerialName("ApplicationName")
    val applicationName: String,

    /**
     * Certification code of the Sale System software (e.g. checksum or certification number).
     */
    @SerialName("CertificationCode")
    val certificationCode: String,

    /**
     * Name of the Sale System software manufacturer.
     */
    @SerialName("ManufacturerID")
    val manufacturerID: String,

    /**
     * Version of the Sale System software.
     */
    @SerialName("SoftwareVersion")
    val softwareVersion: String
)

/**
 * Content of the Logout Request message, ending the association between a Sale Terminal and
 * a POI Terminal.
 */
@Serializable
data class LogoutRequest (
    /**
     * When true, indicates the POI may enter maintenance mode after the session is closed.
     * Default false.
     */
    @SerialName("MaintenanceAllowed")
    val maintenanceAllowed: Boolean? = null
)

/**
 * Message header common to all Sale to POI protocol messages, conveying protocol management
 * information.
 */
@Serializable
data class MessageHeader (
    /**
     * Unique identification of a Device message pair. Mandatory for Device class messages.
     */
    @SerialName("DeviceID")
    val deviceID: String? = null,

    @SerialName("MessageCategory")
    val messageCategory: MessageCategoryType,

    @SerialName("MessageClass")
    val messageClass: MessageClassType,

    @SerialName("MessageType")
    val messageType: MessageTypeType,

    /**
     * Hierarchical identification of the POI System or POI Terminal. Its scope is limited to
     * the Sale to POI protocol.
     */
    @SerialName("POIID")
    val poiid: String,

    /**
     * Highest version of the Sale to POI protocol managed by the sender. Mandatory in Login and
     * Diagnosis messages.
     */
    @SerialName("ProtocolVersion")
    val protocolVersion: String? = null,

    /**
     * Hierarchical identification of the Sale System or Sale Terminal. Its scope is limited to
     * the Sale to POI protocol.
     */
    @SerialName("SaleID")
    val saleID: String,

    /**
     * Unique identification of a Service or Event message pair between a Sale and POI
     * component. Mandatory for Service and Event class messages.
     */
    @SerialName("ServiceID")
    val serviceID: String? = null
)

/**
 * Class of message: Service (transaction request/response initiated by Sale), Device
 * (device operation), or Event (unsolicited notification from POI).
 */
@Serializable
enum class MessageClassType(val value: String) {
    @SerialName("Device") Device("Device"),
    @SerialName("Event") Event("Event"),
    @SerialName("Service") Service("Service");
}

/**
 * Type of message: Request (requires a response), Response (answers a request), or
 * Notification (unsolicited, no response required).
 */
@Serializable
enum class MessageTypeType(val value: String) {
    @SerialName("Notification") Notification("Notification"),
    @SerialName("Request") Request("Request"),
    @SerialName("Response") Response("Response");
}

/**
 * Content of the PIN Request message, used to request PIN entry, encryption, or
 * verification from the POI Terminal.
 */
@Serializable
data class PINRequest (
    /**
     * Additional data required for PIN verification (e.g. part of PAN for ISO format 0).
     * Optional for PINEnter and PINVerify.
     */
    @SerialName("AdditionalInput")
    val additionalInput: String? = null,

    /**
     * When true, a beep is generated for each key pressed during PIN entry. Default false.
     */
    @SerialName("BeepKeyFlag")
    val beepKeyFlag: Boolean? = null,

    @SerialName("CardholderPIN")
    val cardholderPIN: CardholderPIN? = null,

    /**
     * Identifies the key to use to encrypt the PIN block. Optional for PINEnter.
     */
    @SerialName("KeyReference")
    val keyReference: String? = null,

    /**
     * Maximum time in seconds to wait for PIN entry. Optional for PINEnter.
     */
    @SerialName("MaxWaitingTime")
    val maxWaitingTime: Long? = null,

    /**
     * Identifies the PIN block encryption algorithm. Optional for PINEnter.
     */
    @SerialName("PINEncAlgorithm")
    val pinEncAlgorithm: String? = null,

    @SerialName("PINFormat")
    val pinFormat: PINFormatEnum? = null,

    @SerialName("PINRequestType")
    val pinRequestType: PINRequestTypeEnum,

    /**
     * Identifies the PIN verification method and keys. Optional for PINVerify and PINVerifyOnly.
     */
    @SerialName("PINVerifMethod")
    val pinVerifMethod: String? = null
)

/**
 * Encrypted PIN block and related information, used for PIN entry and verification services.
 */
@Serializable
data class CardholderPIN (
    /**
     * Additional information required for PIN verification, such as part of the PAN for ISO
     * format 0.
     */
    @SerialName("AdditionalInput")
    val additionalInput: String? = null,

    @SerialName("EncrPINBlock")
    val encrPINBlock: ContentInformationType,

    @SerialName("PINFormat")
    val pinFormat: PINFormatEnum
)

/**
 * Format of the PIN block before encryption, per ISO 9564.
 */
@Serializable
enum class PINFormatEnum(val value: String) {
    @SerialName("ISO0") Iso0("ISO0"),
    @SerialName("ISO1") Iso1("ISO1"),
    @SerialName("ISO2") Iso2("ISO2"),
    @SerialName("ISO3") Iso3("ISO3");
}

/**
 * Type of PIN service requested: PINVerify (enter and verify), PINVerifyOnly (verify a
 * pre-entered PIN block), or PINEnter (enter and encrypt only).
 */
@Serializable
enum class PINRequestTypeEnum(val value: String) {
    @SerialName("PINEnter") PINEnter("PINEnter"),
    @SerialName("PINVerify") PINVerify("PINVerify"),
    @SerialName("PINVerifyOnly") PINVerifyOnly("PINVerifyOnly");
}

/**
 * Content of the Print Request message, used to print a document on a printer managed by
 * the receiving system.
 */
@Serializable
data class PrintRequest (
    @SerialName("PrintOutput")
    val printOutput: PrintOutput
)

/**
 * A complete print operation, including the document type, response mode, and content to
 * print.
 */
@Serializable
data class PrintOutput (
    @SerialName("DocumentQualifier")
    val documentQualifier: DocumentQualifierEnum,

    /**
     * When true, this print is integrated into another receipt rather than printed separately.
     * Forces Immediate response mode. Default false.
     */
    @SerialName("IntegratedPrintFlag")
    val integratedPrintFlag: Boolean? = null,

    @SerialName("OutputContent")
    val outputContent: OutputContent,

    @SerialName("OutputSignature")
    val outputSignature: ContentInformationType? = null,

    /**
     * When true, a physical cardholder signature is required on this printed document. Default
     * false.
     */
    @SerialName("RequiredSignatureFlag")
    val requiredSignatureFlag: Boolean? = null,

    @SerialName("ResponseMode")
    val responseMode: ResponseModeEnum
)

/**
 * Qualification of the document to print: SaleReceipt, CashierReceipt, CustomerReceipt,
 * Document, Voucher, or Journal.
 */
@Serializable
enum class DocumentQualifierEnum(val value: String) {
    @SerialName("CashierReceipt") CashierReceipt("CashierReceipt"),
    @SerialName("CustomerReceipt") CustomerReceipt("CustomerReceipt"),
    @SerialName("Document") Document("Document"),
    @SerialName("Journal") Journal("Journal"),
    @SerialName("SaleReceipt") SaleReceipt("SaleReceipt"),
    @SerialName("Voucher") Voucher("Voucher");
}

/**
 * When the initiator expects a response: NotRequired (no response needed), Immediate
 * (acknowledge receipt), PrintEnd (after printing complete), or SoundEnd (after sound
 * complete).
 */
@Serializable
enum class ResponseModeEnum(val value: String) {
    @SerialName("Immediate") Immediate("Immediate"),
    @SerialName("NotRequired") NotRequired("NotRequired"),
    @SerialName("PrintEnd") PrintEnd("PrintEnd"),
    @SerialName("SoundEnd") SoundEnd("SoundEnd");
}

/**
 * Content of the Reconciliation Request message, specifying the type of reconciliation and
 * optionally targeting specific Acquirers or a previous period.
 */
@Serializable
data class ReconciliationRequest (
    /**
     * Acquirers to include in AcquirerReconciliation or AcquirerSynchronisation. All connected
     * Acquirers are included when absent.
     */
    @SerialName("AcquirerID")
    val acquirerID: List<String>? = null,

    /**
     * Identification of a previous reconciliation period. Mandatory for PreviousReconciliation
     * type.
     */
    @SerialName("POIReconciliationID")
    val poiReconciliationID: String? = null,

    @SerialName("ReconciliationType")
    val reconciliationType: ReconciliationTypeEnum
)

/**
 * Type of reconciliation: SaleReconciliation (close current period without acquirer sync),
 * AcquirerSynchronisation (close with acquirer sync), AcquirerReconciliation (acquirer
 * only), or PreviousReconciliation (result of a previous period).
 */
@Serializable
enum class ReconciliationTypeEnum(val value: String) {
    @SerialName("AcquirerReconciliation") AcquirerReconciliation("AcquirerReconciliation"),
    @SerialName("AcquirerSynchronisation") AcquirerSynchronisation("AcquirerSynchronisation"),
    @SerialName("PreviousReconciliation") PreviousReconciliation("PreviousReconciliation"),
    @SerialName("SaleReconciliation") SaleReconciliation("SaleReconciliation");
}

/**
 * Content of the Sound Request message, used to start or stop a sound, or to set the
 * default volume.
 */
@Serializable
data class SoundRequest (
    @SerialName("ResponseMode")
    val responseMode: ResponseModeEnum? = null,

    @SerialName("SoundAction")
    val soundAction: SoundActionEnum,

    @SerialName("SoundContent")
    val soundContent: SoundContent? = null,

    /**
     * Volume as a percentage of maximum (0 = mute). Mandatory for SetDefaultVolume.
     */
    @SerialName("SoundVolume")
    val soundVolume: Long? = null
)

/**
 * Action to perform on sound: StartSound, StopSound, or SetDefaultVolume.
 */
@Serializable
enum class SoundActionEnum(val value: String) {
    @SerialName("SetDefaultVolume") SetDefaultVolume("SetDefaultVolume"),
    @SerialName("StartSound") StartSound("StartSound"),
    @SerialName("StopSound") StopSound("StopSound");
}

/**
 * Content of the sound to play, in one of the supported formats.
 */
@Serializable
data class SoundContent (
    @SerialName("Language")
    val language: String? = null,

    /**
     * Identification of the preloaded sound file or text to play. Mandatory for SoundRef and
     * MessageRef formats.
     */
    @SerialName("ReferenceID")
    val referenceID: String? = null,

    @SerialName("SoundFormat")
    val soundFormat: SoundFormatEnum,

    /**
     * Text to synthesise as sound. Mandatory for Text format.
     */
    @SerialName("Text")
    val text: String? = null
)

/**
 * Format of the sound content: SoundRef (preloaded sound file), MessageRef (reference to a
 * preloaded text to play), or Text (text to synthesise).
 */
@Serializable
enum class SoundFormatEnum(val value: String) {
    @SerialName("MessageRef") MessageRef("MessageRef"),
    @SerialName("SoundRef") SoundRef("SoundRef"),
    @SerialName("Text") Text("Text");
}

/**
 * Content of the Stored Value Request message, conveying information for loading,
 * reloading, or activating stored value cards.
 */
@Serializable
data class StoredValueRequest (
    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    @SerialName("SaleData")
    val saleData: SaleData,

    @SerialName("StoredValueData")
    val storedValueData: List<StoredValueData>
)

/**
 * Data related to a stored value card operation (activate, load, unload, etc.) for one card
 * in a StoredValue request.
 */
@Serializable
data class StoredValueData (
    @SerialName("Currency")
    val currency: String,

    /**
     * EAN/UPC code of the stored value product.
     */
    @SerialName("EanUpc")
    val eanUpc: String? = null,

    @SerialName("ItemAmount")
    val itemAmount: Double,

    @SerialName("OriginalPOITransaction")
    val originalPOITransaction: OriginalPOITransaction? = null,

    /**
     * Product code identifying the stored value product (e.g. gift card type, phone top-up
     * operator).
     */
    @SerialName("ProductCode")
    val productCode: String? = null,

    @SerialName("StoredValueAccountID")
    val storedValueAccountID: StoredValueAccountID? = null,

    /**
     * Identification of the stored value provider/host when not identifiable from the product
     * code alone.
     */
    @SerialName("StoredValueProvider")
    val storedValueProvider: String? = null,

    @SerialName("StoredValueTransactionType")
    val storedValueTransactionType: StoredValueTransactionTypeEnum
)

/**
 * Type of operation to perform on a stored value account: Reserve, Activate, Load, Unload,
 * Reverse, or Duplicate.
 */
@Serializable
enum class StoredValueTransactionTypeEnum(val value: String) {
    @SerialName("Activate") Activate("Activate"),
    @SerialName("Duplicate") Duplicate("Duplicate"),
    @SerialName("Load") Load("Load"),
    @SerialName("Reserve") Reserve("Reserve"),
    @SerialName("Reverse") Reverse("Reverse"),
    @SerialName("Unload") Unload("Unload");
}

/**
 * Content of the Transaction Status Request message, used to query the result of a previous
 * transaction when no response was received.
 */
@Serializable
data class TransactionStatusRequest (
    @SerialName("DocumentQualifier")
    val documentQualifier: List<DocumentQualifierEnum>? = null,

    @SerialName("MessageReference")
    val messageReference: MessageReference? = null,

    /**
     * When true, requests the POI to reprint the receipt of the original transaction. Default
     * false.
     */
    @SerialName("ReceiptReprintFlag")
    val receiptReprintFlag: Boolean? = null
)

/**
 * Content of the Transmit Request message, used to send a message to a remote host using
 * the other party as a communication gateway.
 */
@Serializable
data class TransmitRequest (
    /**
     * Transport address of the destination host (IP address or DNS name, optionally followed by
     * ':' and port number).
     */
    @SerialName("DestinationAddress")
    val destinationAddress: String,

    /**
     * Maximum time in seconds for the transmission, including waiting for a response.
     */
    @SerialName("MaximumTransmitTime")
    val maximumTransmitTime: Long,

    /**
     * Base64-encoded message content to transmit to the destination host.
     */
    @SerialName("Message")
    val message: String,

    /**
     * When true, waits for a response from the destination host before replying. Default true.
     */
    @SerialName("WaitResponseFlag")
    val waitResponseFlag: Boolean? = null
)

@Serializable
data class SaleToPOIResponse (
    @SerialName("MessageHeader")
    val messageHeader: MessageHeader,

    @SerialName("SecurityTrailer")
    val securityTrailer: ContentInformationType? = null,

    @SerialName("AdminResponse")
    val adminResponse: AdminResponse? = null,

    @SerialName("BalanceInquiryResponse")
    val balanceInquiryResponse: BalanceInquiryResponse? = null,

    @SerialName("BatchResponse")
    val batchResponse: BatchResponse? = null,

    @SerialName("CardAcquisitionResponse")
    val cardAcquisitionResponse: CardAcquisitionResponse? = null,

    @SerialName("CardReaderAPDUResponse")
    val cardReaderAPDUResponse: List<CardReaderAPDUResponse>? = null,

    @SerialName("CardReaderInitResponse")
    val cardReaderInitResponse: CardReaderInitResponse? = null,

    @SerialName("CardReaderPowerOffResponse")
    val cardReaderPowerOffResponse: CardReaderPowerOffResponse? = null,

    @SerialName("DiagnosisResponse")
    val diagnosisResponse: DiagnosisResponse? = null,

    @SerialName("DisplayResponse")
    val displayResponse: DisplayResponse? = null,

    @SerialName("EnableServiceResponse")
    val enableServiceResponse: EnableServiceResponse? = null,

    @SerialName("GetTotalsResponse")
    val getTotalsResponse: GetTotalsResponse? = null,

    @SerialName("InputResponse")
    val inputResponse: InputResponse? = null,

    @SerialName("LoginResponse")
    val loginResponse: LoginResponse? = null,

    @SerialName("LogoutResponse")
    val logoutResponse: LogoutResponse? = null,

    @SerialName("LoyaltyResponse")
    val loyaltyResponse: LoyaltyResponse? = null,

    @SerialName("PaymentResponse")
    val paymentResponse: PaymentResponse? = null,

    @SerialName("PINResponse")
    val pinResponse: PINResponse? = null,

    @SerialName("PrintResponse")
    val printResponse: PrintResponse? = null,

    @SerialName("ReconciliationResponse")
    val reconciliationResponse: ReconciliationResponse? = null,

    @SerialName("ReversalResponse")
    val reversalResponse: ReversalResponse? = null,

    @SerialName("SoundResponse")
    val soundResponse: SoundResponse? = null,

    @SerialName("StoredValueResponse")
    val storedValueResponse: StoredValueResponse? = null,

    @SerialName("TransactionStatusResponse")
    val transactionStatusResponse: TransactionStatusResponse? = null,

    @SerialName("TransmitResponse")
    val transmitResponse: TransmitResponse? = null
)

/**
 * Content of the Admin Response message.
 */
@Serializable
data class AdminResponse (
    @SerialName("Response")
    val response: Response
)

/**
 * Result of processing a message request, included as the first element of every response
 * message body.
 */
@Serializable
data class Response (
    /**
     * Additional information about the processing result for logging and further analysis.
     * Mandatory when Result is Failure.
     */
    @SerialName("AdditionalResponse")
    val additionalResponse: String? = null,

    /**
     * Condition that produced the failure. Mandatory when Result is Failure.
     */
    @SerialName("ErrorCondition")
    val errorCondition: ErrorConditionType? = null,

    @SerialName("Result")
    val result: ResultType
)

/**
 * Condition that produced the failure. Mandatory when Result is Failure.
 *
 * Condition that produced a failure, allowing the requestor to determine the appropriate
 * resolution action.
 *
 * Error condition for this Acquirer's reconciliation when Result is Partial.
 */
@Serializable
enum class ErrorConditionType(val value: String) {
    @SerialName("Aborted") Aborted("Aborted"),
    @SerialName("Busy") Busy("Busy"),
    @SerialName("Cancel") Cancel("Cancel"),
    @SerialName("DeviceOut") DeviceOut("DeviceOut"),
    @SerialName("InProgress") InProgress("InProgress"),
    @SerialName("InsertedCard") InsertedCard("InsertedCard"),
    @SerialName("InvalidCard") InvalidCard("InvalidCard"),
    @SerialName("LoggedOut") LoggedOut("LoggedOut"),
    @SerialName("MessageFormat") MessageFormat("MessageFormat"),
    @SerialName("NotAllowed") NotAllowed("NotAllowed"),
    @SerialName("NotFound") NotFound("NotFound"),
    @SerialName("PaymentRestriction") PaymentRestriction("PaymentRestriction"),
    @SerialName("Refusal") Refusal("Refusal"),
    @SerialName("UnavailableDevice") UnavailableDevice("UnavailableDevice"),
    @SerialName("UnavailableService") UnavailableService("UnavailableService"),
    @SerialName("UnreachableHost") UnreachableHost("UnreachableHost"),
    @SerialName("WrongPIN") WrongPIN("WrongPIN");
}

/**
 * Global result of the processing of a message request: Success, Failure, or Partial (e.g.
 * only partial amount authorised).
 */
@Serializable
enum class ResultType(val value: String) {
    @SerialName("Failure") Failure("Failure"),
    @SerialName("Partial") Partial("Partial"),
    @SerialName("Success") Success("Success");
}

/**
 * Content of the Balance Inquiry Response message, conveying account balances and
 * identification.
 */
@Serializable
data class BalanceInquiryResponse (
    @SerialName("LoyaltyAccountStatus")
    val loyaltyAccountStatus: LoyaltyAccountStatus? = null,

    @SerialName("PaymentAccountStatus")
    val paymentAccountStatus: PaymentAccountStatus? = null,

    @SerialName("PaymentReceipt")
    val paymentReceipt: List<PaymentReceipt>? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * Result of a loyalty account balance inquiry, including account identification, balance,
 * and unit.
 */
@Serializable
data class LoyaltyAccountStatus (
    @SerialName("Currency")
    val currency: String? = null,

    /**
     * Current balance of the loyalty account.
     */
    @SerialName("CurrentBalance")
    val currentBalance: Double? = null,

    @SerialName("LoyaltyAccount")
    val loyaltyAccount: LoyaltyAccount,

    @SerialName("LoyaltyUnit")
    val loyaltyUnit: LoyaltyUnitEnum? = null
)

/**
 * Data identifying a loyalty account and its associated loyalty program brand.
 */
@Serializable
data class LoyaltyAccount (
    @SerialName("LoyaltyAccountID")
    val loyaltyAccountID: LoyaltyAccountID,

    /**
     * Name of the loyalty program brand as known by the Sale System.
     */
    @SerialName("LoyaltyBrand")
    val loyaltyBrand: String? = null
)

/**
 * Result of a payment account balance inquiry, including instrument data, balance,
 * currency, and acquirer information.
 */
@Serializable
data class PaymentAccountStatus (
    @SerialName("Currency")
    val currency: String? = null,

    /**
     * Current balance of the payment account.
     */
    @SerialName("CurrentBalance")
    val currentBalance: Double? = null,

    @SerialName("PaymentAcquirerData")
    val paymentAcquirerData: PaymentAcquirerData? = null,

    @SerialName("PaymentInstrumentData")
    val paymentInstrumentData: PaymentInstrumentData? = null
)

/**
 * Data related to the payment Acquirer's response, including merchant and terminal
 * identification and transaction approval details.
 */
@Serializable
data class PaymentAcquirerData (
    /**
     * Identification of the Acquirer. Present when the POI System is multi-acquirer.
     */
    @SerialName("AcquirerID")
    val acquirerID: String? = null,

    /**
     * Identification of the POI System or Terminal for the Acquirer.
     */
    @SerialName("AcquirerPOIID")
    val acquirerPOIID: String,

    /**
     * Identification of the transaction assigned by the Acquirer, when different from the
     * POITransactionID.
     */
    @SerialName("AcquirerTransactionID")
    val acquirerTransactionID: TransactionIdentificationType? = null,

    /**
     * Code assigned to the transaction by the Acquirer upon approval per ISO 8583 element 38.
     */
    @SerialName("ApprovalCode")
    val approvalCode: String? = null,

    /**
     * Identification of the Acquirer reconciliation period to which this transaction belongs.
     */
    @SerialName("HostReconciliationID")
    val hostReconciliationID: String? = null,

    /**
     * Identification of the merchant for the Acquirer per ISO 8583 element 42.
     */
    @SerialName("MerchantID")
    val merchantID: String
)

/**
 * Customer or merchant payment receipt, included in the response when the POI does not
 * implement the Print message exchange (Basic profile).
 */
@Serializable
data class PaymentReceipt (
    @SerialName("DocumentQualifier")
    val documentQualifier: DocumentQualifierEnum,

    /**
     * When true, this receipt is to be integrated into the Sale receipt rather than printed
     * separately. Default false.
     */
    @SerialName("IntegratedPrintFlag")
    val integratedPrintFlag: Boolean? = null,

    @SerialName("OutputContent")
    val outputContent: OutputContent,

    /**
     * When true, a physical cardholder signature is required on this receipt. Default false.
     */
    @SerialName("RequiredSignatureFlag")
    val requiredSignatureFlag: Boolean? = null
)

/**
 * Content of the Batch Response message, conveying the global result and results of
 * performed transactions.
 */
@Serializable
data class BatchResponse (
    @SerialName("PerformedTransaction")
    val performedTransaction: List<PerformedTransaction>? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * Result of a single transaction performed without the Sale System, returned in a Batch
 * response.
 */
@Serializable
data class PerformedTransaction (
    @SerialName("LoyaltyResult")
    val loyaltyResult: List<LoyaltyResult>? = null,

    @SerialName("PaymentResult")
    val paymentResult: PaymentResult? = null,

    @SerialName("POIData")
    val poiData: POIData,

    @SerialName("Response")
    val response: Response,

    /**
     * Amount reversed. Mandatory when this PerformedTransaction is the result of a reversal.
     */
    @SerialName("ReversedAmount")
    val reversedAmount: Double? = null,

    /**
     * Sale transaction identification. Present only when the transaction was generated by the
     * Sale System.
     */
    @SerialName("SaleData")
    val saleData: SaleData? = null
)

/**
 * Result of a loyalty transaction, including account identification, amounts, acquirer
 * data, and any rebates.
 */
@Serializable
data class LoyaltyResult (
    /**
     * Current balance of the loyalty account after the transaction, if provided by the card or
     * host.
     */
    @SerialName("CurrentBalance")
    val currentBalance: Double? = null,

    @SerialName("LoyaltyAccount")
    val loyaltyAccount: LoyaltyAccount,

    @SerialName("LoyaltyAcquirerData")
    val loyaltyAcquirerData: LoyaltyAcquirerData? = null,

    @SerialName("LoyaltyAmount")
    val loyaltyAmount: LoyaltyAmount? = null,

    @SerialName("Rebates")
    val rebates: Rebates? = null
)

/**
 * Data related to the loyalty Acquirer's response for a loyalty transaction.
 */
@Serializable
data class LoyaltyAcquirerData (
    /**
     * Code assigned to the loyalty transaction by the Acquirer upon approval.
     */
    @SerialName("ApprovalCode")
    val approvalCode: String? = null,

    /**
     * Identification of the loyalty Acquirer reconciliation period for this transaction.
     */
    @SerialName("HostReconciliationID")
    val hostReconciliationID: String? = null,

    /**
     * Identification of the loyalty Acquirer. Present in multi-acquirer environments.
     */
    @SerialName("LoyaltyAcquirerID")
    val loyaltyAcquirerID: String? = null,

    @SerialName("LoyaltyTransactionID")
    val loyaltyTransactionID: TransactionIdentificationType? = null
)

/**
 * Rebates awarded as part of a loyalty transaction, either on the total amount or on
 * individual sale items.
 */
@Serializable
data class Rebates (
    /**
     * Short text to print on the receipt for the total rebate, provided by the Acquirer.
     */
    @SerialName("RebateLabel")
    val rebateLabel: String? = null,

    @SerialName("SaleItemRebate")
    val saleItemRebate: List<SaleItemRebate>? = null,

    /**
     * Global rebate amount not attached to a specific item.
     */
    @SerialName("TotalRebate")
    val totalRebate: Double? = null
)

/**
 * Rebate awarded on a specific sale item as part of a loyalty transaction.
 */
@Serializable
data class SaleItemRebate (
    /**
     * EAN/UPC code of the rebated item, if present in the corresponding SaleItem.
     */
    @SerialName("EanUpc")
    val eanUpc: String? = null,

    /**
     * Rebate amount on the line item.
     */
    @SerialName("ItemAmount")
    val itemAmount: Double? = null,

    /**
     * Identification of the sale item within the transaction (links to the corresponding
     * SaleItem).
     */
    @SerialName("ItemID")
    val itemID: Long,

    /**
     * Product code of the rebated item.
     */
    @SerialName("ProductCode")
    val productCode: String,

    /**
     * Quantity of additional free units awarded as rebate.
     */
    @SerialName("Quantity")
    val quantity: Double? = null,

    /**
     * Short text to print on the receipt in front of the rebate, provided by the Acquirer.
     */
    @SerialName("RebateLabel")
    val rebateLabel: String? = null,

    @SerialName("UnitOfMeasure")
    val unitOfMeasure: UnitOfMeasureEnum? = null
)

/**
 * Result of a processed payment transaction, including instrument data, amounts,
 * authentication, and acquirer information.
 */
@Serializable
data class PaymentResult (
    @SerialName("AmountsResp")
    val amountsResp: AmountsResp? = null,

    @SerialName("AuthenticationMethod")
    val authenticationMethod: List<AuthenticationMethodType>? = null,

    @SerialName("CapturedSignature")
    val capturedSignature: CapturedSignature? = null,

    @SerialName("CurrencyConversion")
    val currencyConversion: List<CurrencyConversion>? = null,

    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    @SerialName("Instalment")
    val instalment: Instalment? = null,

    /**
     * When true, the Merchant forced the transaction to be accepted (e.g. via SiteManager
     * confirmation). Default false.
     */
    @SerialName("MerchantOverrideFlag")
    val merchantOverrideFlag: Boolean? = null,

    /**
     * When true, the transaction required online approval from a host. Default true.
     */
    @SerialName("OnlineFlag")
    val onlineFlag: Boolean? = null,

    @SerialName("PaymentAcquirerData")
    val paymentAcquirerData: PaymentAcquirerData? = null,

    @SerialName("PaymentInstrumentData")
    val paymentInstrumentData: PaymentInstrumentData? = null,

    @SerialName("PaymentType")
    val paymentType: PaymentTypeEnum? = null,

    /**
     * CMS-encrypted handwritten signature captured on the POI.
     */
    @SerialName("ProtectedSignature")
    val protectedSignature: ContentInformationType? = null,

    /**
     * End of the validity period for a reservation (OneTimeReservation, FirstReservation,
     * UpdateReservation).
     */
    @SerialName("ValidityDate")
    val validityDate: String? = null
)

/**
 * Various amounts in the payment response approved by the POI and the Acquirer.
 */
@Serializable
data class AmountsResp (
    /**
     * Amount authorised by the Acquirer. Equals RequestedAmount + TotalFeesAmount for full
     * authorisation.
     */
    @SerialName("AuthorizedAmount")
    val authorizedAmount: Double,

    /**
     * Actual cashback amount performed with the payment.
     */
    @SerialName("CashBackAmount")
    val cashBackAmount: Double? = null,

    /**
     * Currency of the response amounts. Mandatory for currency conversion.
     */
    @SerialName("Currency")
    val currency: String? = null,

    /**
     * Actual tip amount included in the authorised amount.
     */
    @SerialName("TipAmount")
    val tipAmount: Double? = null,

    /**
     * Total financial fees charged for the payment service.
     */
    @SerialName("TotalFeesAmount")
    val totalFeesAmount: Double? = null,

    /**
     * Total rebates amount across all loyalty programs.
     */
    @SerialName("TotalRebatesAmount")
    val totalRebatesAmount: Double? = null
)

/**
 * Methods used for customer authentication during the payment transaction. Informs the Sale
 * System how the cardholder was authenticated.
 */
@Serializable
enum class AuthenticationMethodType(val value: String) {
    @SerialName("Bypass") Bypass("Bypass"),
    @SerialName("ManualVerification") ManualVerification("ManualVerification"),
    @SerialName("MerchantAuthentication") MerchantAuthentication("MerchantAuthentication"),
    @SerialName("OfflinePIN") OfflinePIN("OfflinePIN"),
    @SerialName("OnLinePIN") OnLinePIN("OnLinePIN"),
    @SerialName("PaperSignature") PaperSignature("PaperSignature"),
    @SerialName("SecureCertificate") SecureCertificate("SecureCertificate"),
    @SerialName("SecureNoCertificate") SecureNoCertificate("SecureNoCertificate"),
    @SerialName("SecuredChannel") SecuredChannel("SecuredChannel"),
    @SerialName("SignatureCapture") SignatureCapture("SignatureCapture"),
    @SerialName("UnknownMethod") UnknownMethod("UnknownMethod");
}

/**
 * Numeric value of a handwritten signature captured on the POI by a signature capture
 * device.
 */
@Serializable
data class CapturedSignature (
    /**
     * Size of the pad area where the signature was written, given as maximum abscissa and
     * ordinate values (max 'FFFF').
     */
    @SerialName("AreaSize")
    val areaSize: AreaSize? = null,

    @SerialName("SignaturePoint")
    val signaturePoint: List<SignaturePoint>
)

/**
 * Size of the pad area where the signature was written, given as maximum abscissa and
 * ordinate values (max 'FFFF').
 */
@Serializable
data class AreaSize (
    @SerialName("X")
    val x: String,

    @SerialName("Y")
    val y: String
)

/**
 * Coordinates of a point where the pen changes direction or is lifted during signature
 * capture. Both X and Y equal 'FFFF' when the pen is lifted.
 */
@Serializable
data class SignaturePoint (
    /**
     * Hexadecimal abscissa value of the signature point (e.g. '3BC', '0', '1287').
     */
    @SerialName("X")
    val x: String,

    /**
     * Hexadecimal ordinate value of the signature point.
     */
    @SerialName("Y")
    val y: String
)

/**
 * Information related to a dynamic currency conversion performed during the payment
 * transaction.
 */
@Serializable
data class CurrencyConversion (
    /**
     * Commission amount charged for the currency conversion.
     */
    @SerialName("Commission")
    val commission: Double? = null,

    /**
     * The payment amount expressed in the customer's home currency after conversion.
     */
    @SerialName("ConvertedAmount")
    val convertedAmount: ConvertedAmount,

    /**
     * Indicates whether the customer approved the currency conversion. Default true.
     */
    @SerialName("CustomerApprovedFlag")
    val customerApprovedFlag: Boolean? = null,

    /**
     * Declaration text to be presented to and printed for the customer.
     */
    @SerialName("Declaration")
    val declaration: String? = null,

    /**
     * Markup percentage applied to the conversion.
     */
    @SerialName("Markup")
    val markup: Double? = null,

    /**
     * Conversion rate from the source currency (AmountsResp.Currency) to the target currency
     * (ConvertedAmount.Currency).
     */
    @SerialName("Rate")
    val rate: Double? = null
)

/**
 * The payment amount expressed in the customer's home currency after conversion.
 */
@Serializable
data class ConvertedAmount (
    @SerialName("AmountValue")
    val amountValue: Double,

    @SerialName("Currency")
    val currency: String
)

/**
 * POI System transaction identification data returned in payment, loyalty, and related
 * response messages.
 */
@Serializable
data class POIData (
    /**
     * Identification of the reconciliation period to which this transaction belongs. Present
     * when Result is Success or Partial.
     */
    @SerialName("POIReconciliationID")
    val poiReconciliationID: String? = null,

    /**
     * Unique identification of the transaction assigned by the POI Terminal. Mandatory in all
     * response messages.
     */
    @SerialName("POITransactionID")
    val poiTransactionID: TransactionIdentificationType
)

/**
 * Content of the Card Acquisition Response message, conveying the read card data and
 * available payment/loyalty brands.
 */
@Serializable
data class CardAcquisitionResponse (
    @SerialName("CustomerLanguage")
    val customerLanguage: String? = null,

    @SerialName("CustomerOrder")
    val customerOrder: List<CustomerOrder>? = null,

    /**
     * Loyalty accounts identified on the presented card(s).
     */
    @SerialName("LoyaltyAccount")
    val loyaltyAccount: List<LoyaltyAccount>? = null,

    /**
     * Payment brands available on the presented card. Multiple values when the customer has not
     * yet selected one.
     */
    @SerialName("PaymentBrand")
    val paymentBrand: List<String>? = null,

    @SerialName("PaymentInstrumentData")
    val paymentInstrumentData: PaymentInstrumentData? = null,

    @SerialName("POIData")
    val poiData: POIData,

    @SerialName("Response")
    val response: Response,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Content of a single APDU response received from the smart card per ISO 7816.
 */
@Serializable
data class CardReaderAPDUResponse (
    /**
     * Data field of the APDU response from the chip card.
     */
    @SerialName("APDUData")
    val apduData: String? = null,

    /**
     * Status words (SW1-SW2) from the APDU response per ISO 7816-4. Base64-encoded 2 bytes.
     */
    @SerialName("CardStatusWords")
    val cardStatusWords: String,

    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Card Reader Init Response message, conveying the card entry mode and read
 * data.
 */
@Serializable
data class CardReaderInitResponse (
    @SerialName("EntryMode")
    val entryMode: List<EntryModeType>? = null,

    @SerialName("ICCResetData")
    val iccResetData: ICCResetData? = null,

    @SerialName("Response")
    val response: Response,

    @SerialName("TrackData")
    val trackData: List<TrackData>? = null
)

/**
 * Data from a chip card returned after reset during CardReaderInit, including the Answer To
 * Reset and status words.
 */
@Serializable
data class ICCResetData (
    /**
     * Base64-encoded Answer To Reset (ATR) value from the chip card per ISO 7816-3.
     */
    @SerialName("ATRValue")
    val atrValue: String? = null,

    /**
     * Base64-encoded status words (SW1-SW2) from the chip card per ISO 7816-4.
     */
    @SerialName("CardStatusWords")
    val cardStatusWords: String? = null
)

/**
 * Content of the Card Reader Power-Off Response message.
 */
@Serializable
data class CardReaderPowerOffResponse (
    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Diagnosis Response message, conveying the POI Terminal status and
 * optionally host reachability.
 */
@Serializable
data class DiagnosisResponse (
    @SerialName("HostStatus")
    val hostStatus: List<HostStatus>? = null,

    /**
     * Identifications of Sale Terminals currently logged to this POI Terminal.
     */
    @SerialName("LoggedSaleID")
    val loggedSaleID: List<String>? = null,

    @SerialName("POIStatus")
    val poiStatus: POIStatus? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * Reachability status of a payment or loyalty host, returned in Diagnosis responses when
 * HostDiagnosisFlag is true.
 */
@Serializable
data class HostStatus (
    /**
     * Identification of the Acquirer or host.
     */
    @SerialName("AcquirerID")
    val acquirerID: String,

    /**
     * When true, the host is reachable from the POI. Default true.
     */
    @SerialName("IsReachableFlag")
    val isReachableFlag: Boolean? = null
)

/**
 * Operational status of a POI Terminal and its hardware components.
 */
@Serializable
data class POIStatus (
    /**
     * When true, the card reader is operational. Absent if no card reader is present.
     */
    @SerialName("CardReaderOKFlag")
    val cardReaderOKFlag: Boolean? = null,

    @SerialName("CashHandlingDevice")
    val cashHandlingDevice: List<CashHandlingDevice>? = null,

    /**
     * When true, the communication infrastructure is operational. Absent if no communication
     * module is present.
     */
    @SerialName("CommunicationOKFlag")
    val communicationOKFlag: Boolean? = null,

    /**
     * When true, the POI has detected a fraud suspicion (e.g. unexpected reboot). Default false.
     */
    @SerialName("FraudPreventionFlag")
    val fraudPreventionFlag: Boolean? = null,

    @SerialName("GlobalStatus")
    val globalStatus: GlobalStatusEnum,

    /**
     * When true, the PIN Entry Device is operational. Absent if no PED is present.
     */
    @SerialName("PEDOKFlag")
    val pedokFlag: Boolean? = null,

    @SerialName("PrinterStatus")
    val printerStatus: PrinterStatusEnum? = null,

    /**
     * When true, the security module is operational. Absent if no security module is present.
     */
    @SerialName("SecurityOKFlag")
    val securityOKFlag: Boolean? = null
)

/**
 * Status and contents of a cash handling device managed by the POI System.
 */
@Serializable
data class CashHandlingDevice (
    /**
     * When true, the cash handling device is operational.
     */
    @SerialName("CashHandlingOKFlag")
    val cashHandlingOKFlag: Boolean,

    @SerialName("CoinsOrBills")
    val coinsOrBills: List<CoinsOrBills>,

    @SerialName("Currency")
    val currency: String
)

/**
 * Number of coins or bills of a specific denomination remaining in a cash handling device.
 */
@Serializable
data class CoinsOrBills (
    /**
     * Number of coins or bills of this denomination. Value 0 means the denomination is depleted.
     */
    @SerialName("Number")
    val number: Long,

    /**
     * Denomination value of the coins or bills.
     */
    @SerialName("UnitValue")
    val unitValue: Double
)

/**
 * Overall operational status of a POI Server or POI Terminal.
 */
@Serializable
enum class GlobalStatusEnum(val value: String) {
    @SerialName("Busy") Busy("Busy"),
    @SerialName("Maintenance") Maintenance("Maintenance"),
    @SerialName("OK") Ok("OK"),
    @SerialName("Unreachable") Unreachable("Unreachable");
}

/**
 * Operational status of the printer device.
 */
@Serializable
enum class PrinterStatusEnum(val value: String) {
    @SerialName("NoPaper") NoPaper("NoPaper"),
    @SerialName("OK") Ok("OK"),
    @SerialName("OutOfOrder") OutOfOrder("OutOfOrder"),
    @SerialName("PaperJam") PaperJam("PaperJam"),
    @SerialName("PaperLow") PaperLow("PaperLow");
}

/**
 * Content of the Display Response message, conveying the result of each display command
 * that required a response.
 */
@Serializable
data class DisplayResponse (
    @SerialName("OutputResult")
    val outputResult: List<OutputResult>
)

/**
 * Result of a single display, print, or input output operation, returned in Display, Print,
 * or Input response messages.
 */
@Serializable
data class OutputResult (
    @SerialName("Device")
    val device: DeviceEnum,

    @SerialName("InfoQualify")
    val infoQualify: InfoQualifyEnum,

    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Enable Service Response message.
 */
@Serializable
data class EnableServiceResponse (
    @SerialName("Response")
    val response: Response
)

/**
 * Content of the GetTotals Response message, conveying current period transaction totals.
 */
@Serializable
data class GetTotalsResponse (
    /**
     * Identification of the current reconciliation period for these totals.
     */
    @SerialName("POIReconciliationID")
    val poiReconciliationID: String,

    @SerialName("Response")
    val response: Response,

    @SerialName("TransactionTotals")
    val transactionTotals: List<TransactionTotals>? = null
)

/**
 * Transaction totals for a specific payment instrument type and set of grouping criteria,
 * returned in Reconciliation and GetTotals responses.
 */
@Serializable
data class TransactionTotals (
    /**
     * Identification of the Acquirer for these totals.
     */
    @SerialName("AcquirerID")
    val acquirerID: String? = null,

    /**
     * Payment or loyalty card brand for these totals. Present when configured to break down
     * totals per card brand.
     */
    @SerialName("CardBrand")
    val cardBrand: String? = null,

    /**
     * Error condition for this Acquirer's reconciliation when Result is Partial.
     */
    @SerialName("ErrorCondition")
    val errorCondition: ErrorConditionType? = null,

    /**
     * Identification of the Acquirer reconciliation period for these totals.
     */
    @SerialName("HostReconciliationID")
    val hostReconciliationID: String? = null,

    @SerialName("LoyaltyCurrency")
    val loyaltyCurrency: String? = null,

    @SerialName("LoyaltyTotals")
    val loyaltyTotals: List<LoyaltyTotal>? = null,

    @SerialName("LoyaltyUnit")
    val loyaltyUnit: LoyaltyUnitEnum? = null,

    /**
     * Cashier/operator identification for these totals. Present when requested.
     */
    @SerialName("OperatorID")
    val operatorID: String? = null,

    @SerialName("PaymentCurrency")
    val paymentCurrency: String? = null,

    @SerialName("PaymentInstrumentType")
    val paymentInstrumentType: PaymentInstrumentTypeEnum,

    @SerialName("PaymentTotals")
    val paymentTotals: List<PaymentTotal>? = null,

    /**
     * POI Terminal identification for these totals. Present when requested.
     */
    @SerialName("POIID")
    val poiid: String? = null,

    /**
     * Sale Terminal identification for these totals. Present when requested.
     */
    @SerialName("SaleID")
    val saleID: String? = null,

    /**
     * Shift number for these totals. Present when requested.
     */
    @SerialName("ShiftNumber")
    val shiftNumber: String? = null,

    /**
     * Sale group identification for these totals. Present when requested.
     */
    @SerialName("TotalsGroupID")
    val totalsGroupID: String? = null
)

/**
 * Totals of loyalty transactions of a specific type during the reconciliation period.
 */
@Serializable
data class LoyaltyTotal (
    /**
     * Sum of amounts of loyalty transactions of this type during the period.
     */
    @SerialName("TransactionAmount")
    val transactionAmount: Double,

    /**
     * Number of loyalty transactions of this type during the period.
     */
    @SerialName("TransactionCount")
    val transactionCount: Long,

    @SerialName("TransactionType")
    val transactionType: TransactionTypeEnum
)

/**
 * Type of transaction for grouping totals in reconciliation or GetTotals responses.
 */
@Serializable
enum class TransactionTypeEnum(val value: String) {
    @SerialName("Award") Award("Award"),
    @SerialName("CashAdvance") CashAdvance("CashAdvance"),
    @SerialName("CompletedDeffered") CompletedDeffered("CompletedDeffered"),
    @SerialName("CompletedReservation") CompletedReservation("CompletedReservation"),
    @SerialName("Credit") Credit("Credit"),
    @SerialName("Debit") Debit("Debit"),
    @SerialName("Declined") Declined("Declined"),
    @SerialName("Failed") Failed("Failed"),
    @SerialName("FirstReservation") FirstReservation("FirstReservation"),
    @SerialName("IssuerInstalment") IssuerInstalment("IssuerInstalment"),
    @SerialName("OneTimeReservation") OneTimeReservation("OneTimeReservation"),
    @SerialName("Rebate") Rebate("Rebate"),
    @SerialName("Redemption") Redemption("Redemption"),
    @SerialName("ReverseAward") ReverseAward("ReverseAward"),
    @SerialName("ReverseCredit") ReverseCredit("ReverseCredit"),
    @SerialName("ReverseDebit") ReverseDebit("ReverseDebit"),
    @SerialName("ReverseRebate") ReverseRebate("ReverseRebate"),
    @SerialName("ReverseRedemption") ReverseRedemption("ReverseRedemption"),
    @SerialName("UpdateReservation") UpdateReservation("UpdateReservation");
}

/**
 * Totals of payment transactions of a specific type during the reconciliation period.
 */
@Serializable
data class PaymentTotal (
    /**
     * Sum of amounts of processed transactions of this type during the period.
     */
    @SerialName("TransactionAmount")
    val transactionAmount: Double,

    /**
     * Number of processed transactions of this type during the period.
     */
    @SerialName("TransactionCount")
    val transactionCount: Long,

    @SerialName("TransactionType")
    val transactionType: TransactionTypeEnum
)

/**
 * Content of the Input Response message, conveying the display result and the entered data.
 */
@Serializable
data class InputResponse (
    @SerialName("InputResult")
    val inputResult: InputResult,

    @SerialName("OutputResult")
    val outputResult: OutputResult? = null
)

/**
 * Result of an input command, including the entered data.
 */
@Serializable
data class InputResult (
    @SerialName("Device")
    val device: DeviceEnum,

    @SerialName("InfoQualify")
    val infoQualify: InfoQualifyEnum,

    /**
     * Data entered by the user in response to the input command.
     */
    @SerialName("Input")
    val input: Input? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * Data entered by the user in response to the input command.
 */
@Serializable
data class Input (
    /**
     * User's yes/no response to GetConfirmation or SiteManager. Mandatory for those commands.
     */
    @SerialName("ConfirmedFlag")
    val confirmedFlag: Boolean? = null,

    /**
     * Digit string entered by the user. Mandatory for DigitString.
     */
    @SerialName("DigitInput")
    val digitInput: String? = null,

    /**
     * Number of the function key pressed. Mandatory for GetFunctionKey.
     */
    @SerialName("FunctionKey")
    val functionKey: String? = null,

    @SerialName("InputCommand")
    val inputCommand: InputCommandEnum,

    /**
     * Index(es) of selected menu entries (1-based). Value -1 means Back, 0 means Home.
     * Mandatory for GetMenuEntry.
     */
    @SerialName("MenuEntryNumber")
    val menuEntryNumber: List<Long>? = null,

    /**
     * CMS-protected password. Mandatory for Password command when encryption is used.
     */
    @SerialName("Password")
    val password: ContentInformationType? = null,

    /**
     * Alphanumeric string entered by the user. Mandatory for TextString and DecimalString, or
     * for plaintext Password.
     */
    @SerialName("TextInput")
    val textInput: String? = null
)

/**
 * Content of the Login Response message, conveying POI System identification, terminal
 * characteristics, and status.
 */
@Serializable
data class LoginResponse (
    /**
     * POI System information returned on successful login.
     */
    @SerialName("POISystemData")
    val poiSystemData: POISystemData? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * POI System information returned on successful login.
 */
@Serializable
data class POISystemData (
    /**
     * Date and time of the POI System or POI Terminal for clock synchronisation.
     */
    @SerialName("DateTime")
    val dateTime: String,

    @SerialName("POISoftware")
    val poiSoftware: SaleSoftware,

    @SerialName("POIStatus")
    val poiStatus: POIStatus? = null,

    /**
     * Characteristics of the POI Terminal attached to this Sale Terminal.
     */
    @SerialName("POITerminalData")
    val poiTerminalData: POITerminalData? = null
)

/**
 * Characteristics of the POI Terminal attached to this Sale Terminal.
 */
@Serializable
data class POITerminalData (
    @SerialName("POICapabilities")
    val poiCapabilities: List<POICapabilitiesType>,

    /**
     * Functional profile of the POI Terminal for this session.
     */
    @SerialName("POIProfile")
    val poiProfile: SaleProfile? = null,

    /**
     * Serial number of the POI Terminal, used by the Sale to detect hardware changes.
     */
    @SerialName("POISerialNumber")
    val poiSerialNumber: String,

    @SerialName("TerminalEnvironment")
    val terminalEnvironment: TerminalEnvironmentType
)

/**
 * Hardware capabilities of the POI Terminal that the Sale System is allowed to use. Sent in
 * the Login Response to identify available POI Terminal devices.
 */
@Serializable
enum class POICapabilitiesType(val value: String) {
    @SerialName("CashHandling") CashHandling("CashHandling"),
    @SerialName("CashierDisplay") CashierDisplay("CashierDisplay"),
    @SerialName("CashierError") CashierError("CashierError"),
    @SerialName("CashierInput") CashierInput("CashierInput"),
    @SerialName("CustomerDisplay") CustomerDisplay("CustomerDisplay"),
    @SerialName("CustomerError") CustomerError("CustomerError"),
    @SerialName("CustomerInput") CustomerInput("CustomerInput"),
    @SerialName("EMVContactless") EMVContactless("EMVContactless"),
    @SerialName("ICC") Icc("ICC"),
    @SerialName("MagStripe") MagStripe("MagStripe"),
    @SerialName("PrinterDocument") PrinterDocument("PrinterDocument"),
    @SerialName("PrinterReceipt") PrinterReceipt("PrinterReceipt"),
    @SerialName("PrinterVoucher") PrinterVoucher("PrinterVoucher");
}

/**
 * Content of the Logout Response message.
 */
@Serializable
data class LogoutResponse (
    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Loyalty Response message, conveying the result of the loyalty transaction.
 */
@Serializable
data class LoyaltyResponse (
    @SerialName("LoyaltyResult")
    val loyaltyResult: List<LoyaltyResult>? = null,

    @SerialName("PaymentReceipt")
    val paymentReceipt: List<PaymentReceipt>? = null,

    @SerialName("POIData")
    val poiData: POIData,

    @SerialName("Response")
    val response: Response,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Content of the Payment Response message, conveying the result of the payment transaction.
 */
@Serializable
data class PaymentResponse (
    @SerialName("CustomerOrder")
    val customerOrder: List<CustomerOrder>? = null,

    @SerialName("LoyaltyResult")
    val loyaltyResult: List<LoyaltyResult>? = null,

    @SerialName("PaymentReceipt")
    val paymentReceipt: List<PaymentReceipt>? = null,

    @SerialName("PaymentResult")
    val paymentResult: PaymentResult? = null,

    @SerialName("POIData")
    val poiData: POIData,

    @SerialName("Response")
    val response: Response,

    @SerialName("SaleData")
    val saleData: SaleData
)

/**
 * Content of the PIN Response message, conveying the result and optionally the encrypted
 * PIN block.
 */
@Serializable
data class PINResponse (
    @SerialName("CardholderPIN")
    val cardholderPIN: CardholderPIN? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Print Response message, conveying the result of the print request.
 */
@Serializable
data class PrintResponse (
    @SerialName("DocumentQualifier")
    val documentQualifier: DocumentQualifierEnum,

    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Reconciliation Response message, conveying transaction totals for the
 * reconciliation period.
 */
@Serializable
data class ReconciliationResponse (
    /**
     * Identification of the reconciliation period covered by the totals. Absent for
     * AcquirerReconciliation type.
     */
    @SerialName("POIReconciliationID")
    val poiReconciliationID: String? = null,

    @SerialName("ReconciliationType")
    val reconciliationType: ReconciliationTypeEnum,

    @SerialName("Response")
    val response: Response,

    @SerialName("TransactionTotals")
    val transactionTotals: List<TransactionTotals>? = null
)

/**
 * Content of the Reversal Response message, conveying the result of the reversal.
 */
@Serializable
data class ReversalResponse (
    @SerialName("CustomerOrder")
    val customerOrder: List<CustomerOrder>? = null,

    @SerialName("OriginalPOITransaction")
    val originalPOITransaction: OriginalPOITransaction? = null,

    @SerialName("PaymentReceipt")
    val paymentReceipt: List<PaymentReceipt>? = null,

    @SerialName("POIData")
    val poiData: POIData? = null,

    @SerialName("Response")
    val response: Response,

    @SerialName("ReversedAmount")
    val reversedAmount: Double? = null
)

/**
 * Content of the Sound Response message, conveying the result of the sound action.
 */
@Serializable
data class SoundResponse (
    @SerialName("Response")
    val response: Response
)

/**
 * Content of the Stored Value Response message, conveying the result of each stored value
 * card operation.
 */
@Serializable
data class StoredValueResponse (
    @SerialName("POIData")
    val poiData: POIData,

    @SerialName("Response")
    val response: Response,

    @SerialName("SaleData")
    val saleData: SaleData,

    @SerialName("StoredValueResult")
    val storedValueResult: List<StoredValueResult>? = null
)

/**
 * Result of a single stored value card operation within a StoredValue response.
 */
@Serializable
data class StoredValueResult (
    @SerialName("Currency")
    val currency: String,

    /**
     * EAN/UPC code of the stored value product. Copy from request.
     */
    @SerialName("EanUpc")
    val eanUpc: String? = null,

    @SerialName("HostTransactionID")
    val hostTransactionID: TransactionIdentificationType? = null,

    @SerialName("ItemAmount")
    val itemAmount: Double,

    /**
     * Product code of the stored value product. Copy from request.
     */
    @SerialName("ProductCode")
    val productCode: String? = null,

    @SerialName("StoredValueAccountStatus")
    val storedValueAccountStatus: StoredValueAccountStatus,

    @SerialName("StoredValueTransactionType")
    val storedValueTransactionType: StoredValueTransactionTypeEnum
)

/**
 * Result of a stored value card operation, including account identification and current
 * balance.
 */
@Serializable
data class StoredValueAccountStatus (
    /**
     * Current balance of the stored value account after the operation, when known.
     */
    @SerialName("CurrentBalance")
    val currentBalance: Double? = null,

    @SerialName("StoredValueAccountID")
    val storedValueAccountID: StoredValueAccountID
)

/**
 * Content of the Transaction Status Response message, conveying the status of the queried
 * transaction.
 */
@Serializable
data class TransactionStatusResponse (
    @SerialName("MessageReference")
    val messageReference: MessageReference? = null,

    /**
     * The original response message when the queried transaction has completed.
     */
    @SerialName("RepeatedMessageResponse")
    val repeatedMessageResponse: RepeatedMessageResponse? = null,

    @SerialName("Response")
    val response: Response
)

/**
 * The original response message when the queried transaction has completed.
 */
@Serializable
data class RepeatedMessageResponse (
    @SerialName("MessageHeader")
    val messageHeader: MessageHeader,

    @SerialName("RepeatedResponseMessageBody")
    val repeatedResponseMessageBody: RepeatedResponseMessageBody
)

/**
 * Body of a repeated response message returned within a TransactionStatus response.
 */
@Serializable
data class RepeatedResponseMessageBody (
    @SerialName("BatchResponse")
    val batchResponse: BatchResponse? = null,

    @SerialName("CardAcquisitionResponse")
    val cardAcquisitionResponse: CardAcquisitionResponse? = null,

    @SerialName("CardReaderAPDUResponse")
    val cardReaderAPDUResponse: List<CardReaderAPDUResponse>? = null,

    @SerialName("LoyaltyResponse")
    val loyaltyResponse: LoyaltyResponse? = null,

    @SerialName("PaymentResponse")
    val paymentResponse: PaymentResponse? = null,

    @SerialName("ReconciliationResponse")
    val reconciliationResponse: ReconciliationResponse? = null,

    @SerialName("ReversalResponse")
    val reversalResponse: ReversalResponse? = null,

    @SerialName("StoredValueResponse")
    val storedValueResponse: StoredValueResponse? = null
)

/**
 * Content of the Transmit Response message, conveying the result and optionally the
 * received response.
 */
@Serializable
data class TransmitResponse (
    /**
     * Base64-encoded response message received from the destination host.
     */
    @SerialName("Message")
    val message: String? = null,

    @SerialName("Response")
    val response: Response
)
