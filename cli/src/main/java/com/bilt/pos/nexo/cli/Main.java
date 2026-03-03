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
import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.DiagnosisRequest;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageTypeType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.SaleData;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
            run(ip, type, encryption, passphrase, keyId, keyVersion, amount, currency);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Request failed", e);
            System.exit(1);
        }
    }

    private static void run(String ip, String type, boolean encryption,
                            String passphrase, String keyId, int keyVersion,
                            double amount, String currency) throws Exception {

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
            case "diagnosis":
                request = buildDiagnosisRequest(serviceID);
                break;
            default:
                throw new IllegalArgumentException("Unknown request type: " + type + ". Supported: payment, diagnosis");
        }

        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);

        NexoTerminalAPI apiRequest = NexoTerminalAPI.builder()
                .saleToPOIRequest(request)
                .build();

        NexoTerminalAPI apiResponse = client.request(apiRequest);

        SaleToPOIResponse response = apiResponse.getSaleToPOIResponse();
        if (response == null) {
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
                "  --type <payment|diagnosis>   Request type (default: payment)",
                "  --no-encryption              Disable message encryption",
                "  --passphrase <value>         Encryption passphrase",
                "  --key-id <value>             Encryption key identifier",
                "  --key-version <number>       Encryption key version (default: 0)",
                "  --amount <number>            Payment amount (default: 25.00)",
                "  --currency <code>            Currency code (default: USD)",
                "  -h, --help                   Show this help"
        );
        LOG.info(usage);
    }
}
