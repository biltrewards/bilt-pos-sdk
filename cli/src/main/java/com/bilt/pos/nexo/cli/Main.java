/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.cli;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.AbortRequest;
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.DeviceEnum;
import com.bilt.pos.nexo.model.DiagnosisRequest;
import com.bilt.pos.nexo.model.DisplayOutput;
import com.bilt.pos.nexo.model.DisplayRequest;
import com.bilt.pos.nexo.model.InfoQualifyEnum;
import com.bilt.pos.nexo.model.InputCommandEnum;
import com.bilt.pos.nexo.model.InputData;
import com.bilt.pos.nexo.model.InputRequest;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageReference;
import com.bilt.pos.nexo.model.MessageTypeType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.OriginalPOITransaction;
import com.bilt.pos.nexo.model.ReversalReasonEnum;
import com.bilt.pos.nexo.model.ReversalRequest;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.SaleData;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.nexo.model.TransactionStatusRequest;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            System.exit(args.length == 0 ? 1 : 0);
            return;
        }

        String ip = args[0];
        String type = "payment";
        boolean encryption = true;
        String passphrase = null;
        String keyId = null;
        int keyVersion = 0;
        String abortServiceID = null;
        String originalServiceID = null;
        String originalTimestamp = null;
        String reversalReason = "MerchantCancel";
        String statusServiceID = null;
        String prompt = null;
        double amount = 2.50;
        String currency = "USD";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--type":
                    type = requireArg(args, ++i, "--type");
                    break;
                case "--no-encryption":
                    encryption = false;
                    break;
                case "--passphrase":
                    passphrase = requireArg(args, ++i, "--passphrase");
                    break;
                case "--key-id":
                    keyId = requireArg(args, ++i, "--key-id");
                    break;
                case "--key-version":
                    keyVersion = Integer.parseInt(requireArg(args, ++i, "--key-version"));
                    break;
                case "--amount":
                    amount = Double.parseDouble(requireArg(args, ++i, "--amount"));
                    break;
                case "--currency":
                    currency = requireArg(args, ++i, "--currency");
                    break;
                case "--abort-service-id":
                    abortServiceID = requireArg(args, ++i, "--abort-service-id");
                    break;
                case "--original-service-id":
                    originalServiceID = requireArg(args, ++i, "--original-service-id");
                    break;
                case "--original-timestamp":
                    originalTimestamp = requireArg(args, ++i, "--original-timestamp");
                    break;
                case "--reversal-reason":
                    reversalReason = requireArg(args, ++i, "--reversal-reason");
                    break;
                case "--status-service-id":
                    statusServiceID = requireArg(args, ++i, "--status-service-id");
                    break;
                case "--prompt":
                    prompt = requireArg(args, ++i, "--prompt");
                    break;
                default:
                    LOG.severe("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
                    return;
            }
        }

        if (encryption && (passphrase == null || keyId == null)) {
            LOG.severe("Encryption is enabled by default. Provide --passphrase and --key-id, or use --no-encryption.");
            System.exit(1);
            return;
        }

        try {
            run(ip, type, encryption, passphrase, keyId, keyVersion, amount, currency, abortServiceID,
                    originalServiceID, originalTimestamp, reversalReason, statusServiceID, prompt);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Request failed", e);
            System.exit(1);
        }
    }

    private static void run(String ip, String type, boolean encryption,
                            String passphrase, String keyId, int keyVersion,
                            double amount, String currency, String abortServiceID,
                            String originalServiceID, String originalTimestamp,
                            String reversalReason, String statusServiceID,
                            String prompt) throws Exception {

        String endpoint = "https://" + ip + ":8443/nexo";
        String serviceID = UUID.randomUUID().toString().substring(0, 8);

        LOG.info("Sending " + type + " request to " + endpoint + " (encryption=" + encryption + ")");

        BiltNexoTerminalClient.Builder clientBuilder = BiltNexoTerminalClient.builder()
                .endpoint(endpoint)
                .trustAllCertificates();

        if (encryption) {
            SecurityKey key = SecurityKey.builder()
                    .passphrase(passphrase)
                    .keyIdentifier(keyId)
                    .keyVersion(keyVersion)
                    .build();
            clientBuilder.securityKey(key);
        }

        BiltNexoTerminalClient client = clientBuilder.build();

        SaleToPOIRequest request;
        switch (type) {
            case "payment":
                request = buildPaymentRequest(serviceID, amount, currency);
                break;
            case "refund":
                if (originalServiceID != null) {
                    if (originalTimestamp == null) {
                        throw new IllegalArgumentException("--original-timestamp is required for referenced refund requests");
                    }
                    request = buildReferencedRefundRequest(serviceID, originalServiceID, originalTimestamp);
                } else {
                    request = buildUnreferencedRefundRequest(serviceID, amount, currency);
                }
                break;
            case "diagnosis":
                request = buildDiagnosisRequest(serviceID);
                break;
            case "display-standby":
                request = buildDisplayStandbyRequest(serviceID);
                break;
            case "display-receipt":
                request = buildDisplayReceiptRequest(serviceID);
                break;
            case "confirmation":
                request = buildConfirmationRequest(serviceID,
                        prompt != null ? prompt : "Would you like a receipt?");
                break;
            case "signature":
                request = buildSignatureRequest(serviceID,
                        prompt != null ? prompt : "Signature required");
                break;
            case "reversal":
                if (originalServiceID == null) {
                    throw new IllegalArgumentException("--original-service-id is required for reversal requests");
                }
                if (originalTimestamp == null) {
                    throw new IllegalArgumentException("--original-timestamp is required for reversal requests");
                }
                request = buildReversalRequest(serviceID, originalServiceID, originalTimestamp, reversalReason);
                break;
            case "transaction-status":
                request = buildTransactionStatusRequest(serviceID, statusServiceID);
                break;
            case "abort":
                if (abortServiceID == null) {
                    throw new IllegalArgumentException("--abort-service-id is required for abort requests");
                }
                request = buildAbortRequest(serviceID, abortServiceID);
                break;
            default:
                throw new IllegalArgumentException("Unknown request type: " + type
                        + ". Supported: payment, refund, diagnosis, display-standby, display-receipt, confirmation, signature, reversal, transaction-status, abort");
        }

        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);

        NexoTerminalAPI apiRequest = NexoTerminalAPI.builder()
                .saleToPOIRequest(request)
                .build();

        LOG.info("Request:\n" + mapper.writeValueAsString(request));

        NexoTerminalAPI apiResponse = client.request(apiRequest);

        if (apiResponse == null) {
            if ("abort".equals(type)) {
                LOG.info("Abort request accepted (no response body)");
                return;
            }
            LOG.severe("Response did not contain a body");
            System.exit(1);
            return;
        }

        SaleToPOIResponse response = apiResponse.getSaleToPOIResponse();
        if (response == null) {
            if ("abort".equals(type)) {
                LOG.info("Abort request accepted (no response body)");
                return;
            }
            LOG.severe("Response did not contain SaleToPOIResponse");
            System.exit(1);
            return;
        }

        System.out.println(mapper.writeValueAsString(response));
    }

    private static SaleToPOIRequest buildPaymentRequest(String serviceID, double amount, String currency) {
        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.PAYMENT)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .paymentRequest(PaymentRequest.builder()
                        .saleData(SaleData.builder()
                                .saleTransactionID(TransactionIdentificationType.builder()
                                        .transactionID(UUID.randomUUID().toString())
                                        .timeStamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                        .build())
                                .build())
                        .paymentTransaction(PaymentTransaction.builder()
                                .amountsReq(AmountsReq.builder()
                                        .currency(currency)
                                        .requestedAmount(amount)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildReferencedRefundRequest(String serviceID,
                                                                    String originalServiceID, String originalTimestamp) {
        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.PAYMENT)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .paymentRequest(PaymentRequest.builder()
                        .paymentData(PaymentData.builder()
                                .paymentType(PaymentTypeEnum.REFUND)
                                .build())
                        .saleData(SaleData.builder()
                                .saleTransactionID(TransactionIdentificationType.builder()
                                        .transactionID(UUID.randomUUID().toString())
                                        .timeStamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                        .build())
                                .build())
                        .paymentTransaction(PaymentTransaction.builder()
                                .originalPOITransaction(OriginalPOITransaction.builder()
                                        .poiTransactionID(TransactionIdentificationType.builder()
                                                .transactionID(originalServiceID)
                                                .timeStamp(originalTimestamp)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildUnreferencedRefundRequest(String serviceID, double amount, String currency) {
        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.PAYMENT)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .paymentRequest(PaymentRequest.builder()
                        .paymentData(PaymentData.builder()
                                .paymentType(PaymentTypeEnum.REFUND)
                                .build())
                        .saleData(SaleData.builder()
                                .saleTransactionID(TransactionIdentificationType.builder()
                                        .transactionID(UUID.randomUUID().toString())
                                        .timeStamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                        .build())
                                .build())
                        .paymentTransaction(PaymentTransaction.builder()
                                .amountsReq(AmountsReq.builder()
                                        .currency(currency)
                                        .requestedAmount(amount)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildDiagnosisRequest(String serviceID) {
        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.DIAGNOSIS)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .diagnosisRequest(DiagnosisRequest.builder()
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildDisplayStandbyRequest(String serviceID) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<displayPayload xmlns=\"urn:bilt:display:v1\" layout=\"standby.xslt\" version=\"1.0\">\n"
                + "  <standby/>\n"
                + "</displayPayload>";
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.DEVICE)
                        .messageCategory(MessageCategoryType.DISPLAY)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .displayRequest(DisplayRequest.builder()
                        .displayOutput(new DisplayOutput[]{
                                DisplayOutput.builder()
                                        .device(DeviceEnum.CUSTOMER_DISPLAY)
                                        .infoQualify(InfoQualifyEnum.DISPLAY)
                                        .outputContent(OutputContent.builder()
                                                .outputFormat(OutputFormatEnum.XHTML)
                                                .outputXHTML(encoded)
                                                .build())
                                        .build()
                        })
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildDisplayReceiptRequest(String serviceID) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<displayPayload xmlns=\"urn:bilt:display:v1\" layout=\"receipt.xslt\" version=\"1.0\">\n"
                + "  <receipt>\n"
                + "    <header><text>Your items</text></header>\n"
                + "    <lineItems>\n"
                + "      <lineItem kind=\"item\">\n"
                + "        <description>Running shoes</description>\n"
                + "        <quantity>1</quantity>\n"
                + "        <unitPrice><currency>$</currency><value>79.99</value></unitPrice>\n"
                + "        <amount><currency>$</currency><value>79.99</value></amount>\n"
                + "      </lineItem>\n"
                + "      <lineItem kind=\"item\">\n"
                + "        <description>Green T-shirt</description>\n"
                + "        <quantity>2</quantity>\n"
                + "        <unitPrice><currency>$</currency><value>9.89</value></unitPrice>\n"
                + "        <amount><currency>$</currency><value>19.78</value></amount>\n"
                + "      </lineItem>\n"
                + "    </lineItems>\n"
                + "    <subtotal>\n"
                + "      <description>Subtotal</description>\n"
                + "      <amount><currency>$</currency><value>99.77</value></amount>\n"
                + "    </subtotal>\n"
                + "    <tax>\n"
                + "      <taxItem>\n"
                + "        <description>State tax</description>\n"
                + "        <amount><currency>$</currency><value>7.23</value></amount>\n"
                + "      </taxItem>\n"
                + "      <taxTotal>\n"
                + "        <description>Total tax</description>\n"
                + "        <amount><currency>$</currency><value>7.23</value></amount>\n"
                + "      </taxTotal>\n"
                + "    </tax>\n"
                + "    <total>\n"
                + "      <description>Total amount</description>\n"
                + "      <amount><currency>$</currency><value>107.00</value></amount>\n"
                + "    </total>\n"
                + "    <footer><text>Thank you for your purchase!</text></footer>\n"
                + "  </receipt>\n"
                + "</displayPayload>";
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.DEVICE)
                        .messageCategory(MessageCategoryType.DISPLAY)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .displayRequest(DisplayRequest.builder()
                        .displayOutput(new DisplayOutput[]{
                                DisplayOutput.builder()
                                        .device(DeviceEnum.CUSTOMER_DISPLAY)
                                        .infoQualify(InfoQualifyEnum.DISPLAY)
                                        .outputContent(OutputContent.builder()
                                                .outputFormat(OutputFormatEnum.XHTML)
                                                .outputXHTML(encoded)
                                                .build())
                                        .build()
                        })
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildInputRequest(String serviceID, String xmlPayload) {
        String encoded = Base64.getEncoder().encodeToString(
                xmlPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.DEVICE)
                        .messageCategory(MessageCategoryType.INPUT)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .inputRequest(InputRequest.builder()
                        .displayOutput(DisplayOutput.builder()
                                .device(DeviceEnum.CUSTOMER_DISPLAY)
                                .infoQualify(InfoQualifyEnum.DISPLAY)
                                .outputContent(OutputContent.builder()
                                        .outputFormat(OutputFormatEnum.XHTML)
                                        .outputXHTML(encoded)
                                        .build())
                                .build())
                        .inputData(InputData.builder()
                                .device(DeviceEnum.CUSTOMER_INPUT)
                                .infoQualify(InfoQualifyEnum.INPUT)
                                .inputCommand(InputCommandEnum.GET_CONFIRMATION)
                                .maxInputTime(60L)
                                .build())
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildConfirmationRequest(String serviceID, String prompt) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<inputPayload xmlns=\"urn:bilt:input:v1\" version=\"1.0\">\n"
                + "  <display>\n"
                + "    <title>" + escapeXml(prompt) + "</title>\n"
                + "  </display>\n"
                + "  <confirmation/>\n"
                + "</inputPayload>";
        return buildInputRequest(serviceID, xml);
    }

    private static SaleToPOIRequest buildSignatureRequest(String serviceID, String prompt) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<inputPayload xmlns=\"urn:bilt:input:v1\" version=\"1.0\">\n"
                + "  <display>\n"
                + "    <title>" + escapeXml(prompt) + "</title>\n"
                + "  </display>\n"
                + "  <signature/>\n"
                + "</inputPayload>";
        return buildInputRequest(serviceID, xml);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static SaleToPOIRequest buildTransactionStatusRequest(String serviceID, String statusServiceID) {
        TransactionStatusRequest.Builder tsBuilder = TransactionStatusRequest.builder();
        if (statusServiceID != null) {
            tsBuilder.messageReference(MessageReference.builder()
                    .messageCategory(MessageCategoryType.PAYMENT)
                    .serviceID(statusServiceID)
                    .saleID("bilt-cli")
                    .build());
        }

        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.TRANSACTION_STATUS)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .transactionStatusRequest(tsBuilder.build())
                .build();
    }

    private static SaleToPOIRequest buildReversalRequest(String serviceID, String originalServiceID,
                                                            String originalTimestamp, String reversalReason) {
        ReversalReasonEnum reason;
        switch (reversalReason) {
            case "CustCancel":
                reason = ReversalReasonEnum.CUST_CANCEL;
                break;
            case "MerchantCancel":
                reason = ReversalReasonEnum.MERCHANT_CANCEL;
                break;
            case "Malfunction":
                reason = ReversalReasonEnum.MALFUNCTION;
                break;
            case "Unable2Compl":
                reason = ReversalReasonEnum.UNABLE2_COMPL;
                break;
            default:
                throw new IllegalArgumentException("Unknown reversal reason: " + reversalReason
                        + ". Supported: CustCancel, MerchantCancel, Malfunction, Unable2Compl");
        }

        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.REVERSAL)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .reversalRequest(ReversalRequest.builder()
                        .originalPOITransaction(OriginalPOITransaction.builder()
                                .poiTransactionID(TransactionIdentificationType.builder()
                                        .transactionID(originalServiceID)
                                        .timeStamp(originalTimestamp)
                                        .build())
                                .build())
                        .reversalReason(reason)
                        .saleData(SaleData.builder()
                                .saleTransactionID(TransactionIdentificationType.builder()
                                        .transactionID(UUID.randomUUID().toString())
                                        .timeStamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private static SaleToPOIRequest buildAbortRequest(String serviceID, String abortServiceID) {
        return SaleToPOIRequest.builder()
                .messageHeader(MessageHeader.builder()
                        .protocolVersion("3.0")
                        .messageClass(MessageClassType.SERVICE)
                        .messageCategory(MessageCategoryType.ABORT)
                        .messageType(MessageTypeType.REQUEST)
                        .serviceID(serviceID)
                        .saleID("bilt-cli")
                        .poiid("bilt-terminal")
                        .build())
                .abortRequest(AbortRequest.builder()
                        .abortReason("MerchantAbort")
                        .messageReference(MessageReference.builder()
                                .messageCategory(MessageCategoryType.PAYMENT)
                                .serviceID(abortServiceID)
                                .saleID("bilt-cli")
                                .build())
                        .build())
                .build();
    }

    private static String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            LOG.severe("Missing value for " + flag);
            System.exit(1);
        }
        return args[index];
    }

    private static void printUsage() {
        String usage = String.join("\n",
                "Usage: bilt-cli <ip> [options]",
                "",
                "Options:",
                "  --type <payment|refund|diagnosis|display-standby|display-receipt|confirmation|signature|reversal|transaction-status|abort>",
                "  --no-encryption              Disable message encryption",
                "  --passphrase <value>         Encryption passphrase",
                "  --key-id <value>             Encryption key identifier",
                "  --key-version <number>       Encryption key version (default: 0)",
                "  --amount <number>            Payment amount / reversal amount (default: 2.50)",
                "  --currency <code>            Currency code (default: USD)",
                "  --prompt <text>              Prompt text for confirmation/signature requests",
                "  --original-service-id <value> POI transaction ID of the original payment to reverse",
                "  --original-timestamp <value> Timestamp of the original POI transaction (ISO 8601)",
                "  --reversal-reason <value>    Reversal reason: CustCancel, MerchantCancel, Malfunction, Unable2Compl (default: MerchantCancel)",
                "  --status-service-id <value>  ServiceID of the transaction to query status for",
                "  --abort-service-id <value>   ServiceID of the in-progress payment to abort",
                "  -h, --help                   Show this help"
        );
        LOG.info(usage);
    }
}
