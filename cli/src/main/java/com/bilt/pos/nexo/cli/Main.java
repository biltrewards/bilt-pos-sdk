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

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.display.HeaderFooterType;
import com.bilt.pos.display.InputPayload;
import com.bilt.pos.display.LabeledAmountType;
import com.bilt.pos.display.LineItemKindType;
import com.bilt.pos.display.LineItemType;
import com.bilt.pos.display.LineItemsType;
import com.bilt.pos.display.ReceiptType;
import com.bilt.pos.display.TaxType;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.client.BiltTerminalEnvironment;
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
import com.bilt.pos.nexo.model.PaymentInstrumentData;
import com.bilt.pos.nexo.model.PaymentInstrumentTypeEnum;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.StoredValueAccountID;
import com.bilt.pos.nexo.model.StoredValueAccountTypeEnum;
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
import jakarta.xml.bind.JAXBException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /**
     * Strong reference to OkHttp's HTTP/2 frame logger, retained so JUL's
     * (weak-referencing) LogManager cannot garbage-collect the configuration we
     * install in {@link #enablePongLogging()} before OkHttp lazily creates the
     * logger. See that method for details.
     */
    @SuppressWarnings("unused")
    private static Logger http2FrameLogger;

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
        boolean verbose = false;
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
        String cacert = null;
        String hostnamePattern = null;
        Double pingIntervalSeconds = null;
        boolean recoverOnNetworkError = true;
        boolean identifyMember = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--type":
                    type = requireArg(args, ++i, "--type");
                    break;
                case "--no-encryption":
                    encryption = false;
                    break;
                case "--identify":
                    identifyMember = true;
                    break;
                case "--verbose":
                    verbose = true;
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
                case "--cacert":
                    cacert = requireArg(args, ++i, "--cacert");
                    break;
                case "--hostname-pattern":
                    hostnamePattern = requireArg(args, ++i, "--hostname-pattern");
                    break;
                case "--ping-interval":
                    pingIntervalSeconds = Double.parseDouble(requireArg(args, ++i, "--ping-interval"));
                    break;
                case "--no-recover-on-network-error":
                    recoverOnNetworkError = false;
                    break;
                case "--environment": {
                    String env = requireArg(args, ++i, "--environment");
                    switch (env) {
                        case "prod":
                        case "production":
                            hostnamePattern = BiltTerminalEnvironment.PRODUCTION.hostnamePattern();
                            break;
                        case "staging":
                            hostnamePattern = BiltTerminalEnvironment.STAGING.hostnamePattern();
                            break;
                        default:
                            LOG.severe("Unknown environment: " + env + ". Supported: prod, staging");
                            System.exit(1);
                            return;
                    }
                    break;
                }
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

        if (verbose) {
            enableVerboseLogging();
        }

        try {
            run(ip, type, encryption, passphrase, keyId, keyVersion, amount, currency, abortServiceID,
                    originalServiceID, originalTimestamp, reversalReason, statusServiceID, prompt,
                    cacert, hostnamePattern, pingIntervalSeconds, recoverOnNetworkError,
                    identifyMember);
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
                            String prompt, String cacert, String hostnamePattern,
                            Double pingIntervalSeconds, boolean recoverOnNetworkError,
                            boolean identifyMember) throws Exception {

        String endpoint = "https://" + ip + ":8443/nexo";
        String serviceID = UUID.randomUUID().toString().substring(0, 8);

        LOG.info("Sending " + type + " request to " + endpoint + " (encryption=" + encryption + ")");

        BiltNexoTerminalClient.Builder clientBuilder = BiltNexoTerminalClient.builder()
                .endpoint(endpoint);

        if (pingIntervalSeconds != null) {
            Duration pingInterval = Duration.ofMillis((long) (pingIntervalSeconds * 1000));
            LOG.info("Sending keep-alive pings every " + pingIntervalSeconds
                    + "s (HTTP/2 only; ignored on HTTP/1.1)");
            clientBuilder.pingInterval(pingInterval);
        }

        if (recoverOnNetworkError) {
            LOG.info("Recovery on network error active (default; requires terminal-side support)");
        } else {
            LOG.info("Recovery on network error disabled");
            clientBuilder.disableRecoveryOnNetworkError();
        }

        // --cacert and a hostname pattern (--hostname-pattern/--environment) must
        // be provided together to verify TLS. If either is present without the
        // other, fail rather than silently falling back to trustAllCertificates()
        // — an operator who passes --environment must not believe verified TLS is
        // active when it is not. With neither flag, all certificates are trusted
        // (testing only).
        if (cacert != null || hostnamePattern != null) {
            if (cacert == null) {
                throw new IllegalArgumentException(
                        "--environment/--hostname-pattern requires --cacert so the certificate "
                        + "chain can be verified against the Bilt CA. Without it, TLS verification "
                        + "would be disabled despite the hostname pattern.");
            }
            if (hostnamePattern == null) {
                throw new IllegalArgumentException(
                        "--cacert requires --hostname-pattern or --environment so the terminal's "
                        + "certificate hostname (a synthetic SAN) is verified instead of the IP.");
            }
            LOG.info("Verifying TLS against CA certificate: " + cacert);
            LOG.info("Expecting certificate hostname pattern: " + hostnamePattern);
            clientBuilder.trustCertificate(Path.of(cacert))
                    .expectedHostnamePattern(hostnamePattern);
        } else {
            clientBuilder.trustAllCertificates();
        }

        if (encryption) {
            SecurityKey key = SecurityKey.builder()
                    .passphrase(passphrase)
                    .keyIdentifier(keyId)
                    .keyVersion(keyVersion)
                    .build();
            clientBuilder.securityKey(key);
        }

        BiltNexoTerminalClient client = clientBuilder.build();

        if ("session".equals(type)) {
            runCheckoutSessionDemo(client, amount, currency, identifyMember);
            return;
        }

        SaleToPOIRequest request;
        switch (type) {
            case "payment":
                request = buildPaymentRequest(serviceID, amount, currency);
                break;
            case "gift-card":
                request = buildGiftCardPaymentRequest(serviceID, amount, currency);
                break;
            case "refund":
                if (originalServiceID != null) {
                    if (originalTimestamp == null) {
                        throw new IllegalArgumentException("--original-timestamp is required for referenced refund requests");
                    }
                    request = buildReferencedRefundRequest(serviceID, originalServiceID, originalTimestamp, amount, currency);
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
                        + ". Supported: payment, gift-card, refund, diagnosis, display-standby, display-receipt, confirmation, signature, reversal, transaction-status, abort");
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

    private static SaleToPOIRequest buildGiftCardPaymentRequest(String serviceID, double amount, String currency) {
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
                                .paymentInstrumentData(PaymentInstrumentData.builder()
                                        .paymentInstrumentType(PaymentInstrumentTypeEnum.STORED_VALUE)
                                        .storedValueAccountID(StoredValueAccountID.builder()
                                                .storedValueAccountType(StoredValueAccountTypeEnum.GIFT_CARD)
                                                .build())
                                        .build())
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

    private static SaleToPOIRequest buildReferencedRefundRequest(String serviceID,
                                                                    String originalServiceID, String originalTimestamp,
                                                                    double amount, String currency) {
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

    private static SaleToPOIRequest buildDisplayStandbyRequest(String serviceID) throws JAXBException {
        DisplayPayload payload = DisplayPayloadHelper.standby("standby.xslt");
        String encoded = DisplayPayloadHelper.toBase64(payload);

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

    private static SaleToPOIRequest buildDisplayReceiptRequest(String serviceID) throws JAXBException {
        // Build receipt using generated classes
        ReceiptType receipt = new ReceiptType();
        receipt.setHeader(DisplayPayloadHelper.header("Your items"));

        // Line items
        LineItemsType lineItems = new LineItemsType();
        lineItems.getLineItem().add(DisplayPayloadHelper.productItem(
                "Running shoes",
                BigDecimal.ONE,
                "$",
                new BigDecimal("79.99"),
                new BigDecimal("79.99")
        ));
        lineItems.getLineItem().add(DisplayPayloadHelper.productItem(
                "Green T-shirt",
                new BigDecimal("2"),
                "$",
                new BigDecimal("9.89"),
                new BigDecimal("19.78")
        ));
        receipt.setLineItems(lineItems);

        // Subtotal
        receipt.setSubtotal(DisplayPayloadHelper.labeledAmount("Subtotal", "$", 99.77));

        // Tax
        TaxType tax = new TaxType();
        tax.getTaxItem().add(DisplayPayloadHelper.labeledAmount("State tax", "$", 7.23));
        tax.setTaxTotal(DisplayPayloadHelper.labeledAmount("Total tax", "$", 7.23));
        receipt.setTax(tax);

        // Total
        receipt.setTotal(DisplayPayloadHelper.labeledAmount("Total amount", "$", 107.00));
        receipt.setFooter(DisplayPayloadHelper.footer("Thank you for your purchase!"));

        // Build payload
        DisplayPayload payload = new DisplayPayload();
        payload.setVersion("1.0");
        payload.setLayout("receipt.xslt");
        payload.setReceipt(receipt);

        String encoded = DisplayPayloadHelper.toBase64(payload);

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

    private static SaleToPOIRequest buildInputRequest(String serviceID, InputPayload inputPayload) throws JAXBException {
        String encoded = DisplayPayloadHelper.toBase64(inputPayload);

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

    private static SaleToPOIRequest buildConfirmationRequest(String serviceID, String prompt) throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.confirmation(prompt);
        return buildInputRequest(serviceID, payload);
    }

    private static SaleToPOIRequest buildSignatureRequest(String serviceID, String prompt) throws JAXBException {
        InputPayload payload = DisplayPayloadHelper.signature(prompt);
        return buildInputRequest(serviceID, payload);
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

    /**
     * Raise the SDK client logger to {@code FINE} so its encrypt/decrypt trace
     * messages are printed. The SDK logs these at {@code FINE}, which the default
     * JUL configuration suppresses, so attach a dedicated console handler at that
     * level rather than relying on the (INFO-level) root handler.
     */
    private static void enableVerboseLogging() {
        Logger sdkLogger = Logger.getLogger("com.bilt.pos.nexo.client");
        sdkLogger.setLevel(Level.FINE);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        sdkLogger.addHandler(handler);
        sdkLogger.setUseParentHandlers(false);

        enablePongLogging();
    }

    /**
     * Print a message whenever the terminal answers a keep-alive ping.
     *
     * <p>OkHttp exposes no public callback for HTTP/2 pongs, so the only place a
     * received pong is observable is its internal frame logger
     * ({@code okhttp3.internal.http2.Http2}), which logs every frame at
     * {@code FINE} in the form {@code "<< 0x%08x %5d %-13s %s"}. We raise that
     * logger to {@code FINE} but suppress its own output
     * ({@code setUseParentHandlers(false)}) and attach a handler that forwards
     * only inbound {@code PING} frames carrying the {@code ACK} flag — the pong
     * the terminal sends in reply to our ping. Our outbound ping, a
     * server-initiated ping request, and our ACK reply to it are all excluded,
     * so exactly the pongs answering our keep-alive pings are printed.</p>
     */
    private static void enablePongLogging() {
        // Pin the logger in a static field. java.util.logging's LogManager holds
        // loggers only weakly, and OkHttp does not create this logger until it
        // loads Http2Reader during the h2 handshake — after this method returns.
        // Without a strong reference the logger we configure here can be
        // garbage-collected first, so OkHttp would then create a fresh one at the
        // default level, discarding our FINE level and handler and silently
        // dropping the pong output.
        http2FrameLogger = Logger.getLogger("okhttp3.internal.http2.Http2");
        http2FrameLogger.setLevel(Level.FINE);
        http2FrameLogger.setUseParentHandlers(false);
        Handler pongHandler = new Handler() {
            @Override public void publish(LogRecord record) {
                String message = record.getMessage();
                if (message != null && message.startsWith("<<")
                        && message.contains("PING") && message.contains("ACK")) {
                    LOG.info("Pong received from terminal");
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        pongHandler.setLevel(Level.FINE);
        http2FrameLogger.addHandler(pongHandler);
    }

    private static String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            LOG.severe("Missing value for " + flag);
            System.exit(1);
        }
        return args[index];
    }

    /**
     * Waits for Enter so the operator can verify the terminal display
     * before the demo moves on. Skipped silently when stdin is not
     * interactive (piped input or EOF).
     */
    private static void pauseForDisplayCheck(String expectation) {
        System.out.println(">> " + expectation + " — press Enter to continue...");
        try {
            int c;
            do {
                c = System.in.read();
            } while (c != '\n' && c != -1);
        } catch (java.io.IOException ignored) {
            // no interactive stdin; continue without pausing
        }
    }

    /**
     * End-to-end CheckoutSession demo: optional member identification, item
     * scanning with automatic terminal display, tax, and the orchestrated
     * payment sequence with inline step handlers.
     */
    private static void runCheckoutSessionDemo(BiltNexoTerminalClient client,
                                               double amount, String currency,
                                               boolean identifyMember) {
        // try-with-resources: close() sends the end signal best-effort even
        // when a demo step throws, so the terminal never keeps a stale session
        try (com.bilt.pos.session.CheckoutSession session =
                com.bilt.pos.session.CheckoutSession.builder()
                        .client(client)
                        .saleId("bilt-cli")
                        .poiId("bilt-terminal")
                        .currency(currency)
                        .start()
                        .get()) {

            LOG.info("Session " + session.getSessionId() + " started (state "
                    + session.getState() + ")");

            // 1. Identify the member — opt-in via --identify: the terminal-side
            // CardAcquisition prompt immediately asks for a card, which gets in
            // the way when exercising the basket/display flow alone
            if (identifyMember) {
                session.identifyMember()
                        .onSuccess(member -> {
                            if (member.getStatus()
                                    == com.bilt.pos.session.identity.IdentifyStatus.FOUND) {
                                LOG.info("Member " + member.getMemberId() + " ("
                                        + member.getLoyaltyBrand() + "), "
                                        + member.getRewards().size() + " reward(s) available");
                            } else {
                                LOG.info("No member attached (" + member.getStatus()
                                        + "); guest checkout");
                            }
                        })
                        .onError(error -> LOG.warning("Identification failed: " + error))
                        .execute();
            } else {
                LOG.info("Skipping member identification (pass --identify to enable); "
                        + "guest checkout");
            }

            // 2. Scan items — each addItem refreshes the terminal display with
            // the itemised basket (a DisplayRequest); pause so it can be verified
            BigDecimal half = BigDecimal.valueOf(amount / 2).setScale(2, java.math.RoundingMode.HALF_UP);
            com.bilt.pos.session.basket.Basket basket =
                    session.basket().addItem(com.bilt.pos.session.basket.BasketItem.builder()
                            .sku("CLI-DEMO-1").description("Demo Item A").quantity(1)
                            .unitPrice(half).build());
            LOG.info("Added Demo Item A — basket total " + basket.getGrandTotal() + " " + currency);
            pauseForDisplayCheck("Demo Item A should now be on the terminal display");
            basket = session.basket().addItem(com.bilt.pos.session.basket.BasketItem.builder()
                    .sku("CLI-DEMO-2").description("Demo Item B").quantity(1)
                    .unitPrice(BigDecimal.valueOf(amount).subtract(half)).build());
            LOG.info("Added Demo Item B — basket total " + basket.getGrandTotal() + " " + currency);
            pauseForDisplayCheck("Demo Item B should now be on the terminal display");

            // 3. Pay — rebate/point steps run automatically for identified members
            com.bilt.pos.session.payment.CheckoutResult checkout = session.pay()
                    .onRebatesRedeemed(rebates -> {
                        LOG.info("Rebates committed: -" + rebates.getTotalRebateAmount());
                        return rebates.getSuggestedTotal();
                    })
                    .onPointsRedeemed(points -> {
                        LOG.info("Points redeemed: " + points.getPointsUsed()
                                + " (-" + points.getMonetaryValue() + ")");
                        return points.getSuggestedTotal();
                    })
                    .onSuccess(result -> {
                        LOG.info("Payment approved: card " + result.getCardAmountCharged()
                                + " " + currency + ", approval " + result.getApprovalCode()
                                + ", POI txn " + result.getPoiTransactionId());
                        if (result.getTotalPointsEarned() > 0) {
                            LOG.info("Earned " + result.getTotalPointsEarned()
                                    + " points (balance " + result.getPointsBalance() + ")");
                        }
                        result.getWarnings().forEach(w -> LOG.warning("Warning: " + w));
                    })
                    .onError(error -> {
                        LOG.severe("Payment failed: " + error);
                        return com.bilt.pos.session.payment.PaymentOptions.voidAndAbort();
                    })
                    .getOrNull();

            session.end()
                    .onError(error -> LOG.warning("Session end failed: " + error))
                    .execute();

            LOG.info("Session finished in state " + session.getState());
            if (checkout == null) {
                // this demo doubles as a smoke test: a failed checkout must
                // fail the process (main exits 1 on any exception from run())
                throw new IllegalStateException(
                        "checkout did not complete (session state " + session.getState() + ")");
            }
        }
    }

    private static void printUsage() {
        String usage = String.join("\n",
                "Usage: bilt-cli <ip> [options]",
                "",
                "Options:",
                "  --type <payment|gift-card|refund|diagnosis|display-standby|display-receipt|confirmation|signature|reversal|transaction-status|abort|session>",
                "                               'session' runs a full CheckoutSession demo: basket, display, pay",
                "  --identify                   In the session demo, prompt for member identification first",
                "  --no-encryption              Disable message encryption",
                "  --cacert <path>              Verify TLS against this CA/public cert file (PEM or DER).",
                "                               When omitted, all certificates are trusted (testing only).",
                "  --hostname-pattern <pattern> Expected cert hostname pattern, e.g. '*.pos.staging.bilt.dev'",
                "                               (requires --cacert; matches the cert SAN, not the IP)",
                "  --environment <prod|staging> Shorthand for the standard --hostname-pattern of that env",
                "  --verbose                    Log encryption/decryption traces and the negotiated HTTP protocol (HTTP/2 vs HTTP/1.1)",
                "  --ping-interval <seconds>    Send keep-alive pings at this interval while connected, e.g. '1'",
                "                               (HTTP/2 only; ignored on HTTP/1.1). Default: disabled",
                "  --no-recover-on-network-error  Disable network-error recovery (on by default: re-sends the",
                "                               request under a correlation id after a transient drop; needs terminal support)",
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
