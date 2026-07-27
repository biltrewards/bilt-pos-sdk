package com.bilt.pos.session;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.EntryMode;
import com.bilt.pos.session.identity.ForceEntryMode;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.MemberIdentifier;
import com.bilt.pos.session.identity.RewardType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionIdentityTest {

    private static final String REWARDS_JSON = "{\"rewards\":["
            + "{\"rewardRef\":\"rwd:RWD-44021\",\"type\":\"reward\",\"name\":\"$10 Off Purchase\","
            + "\"expirationDate\":\"2026-05-15T23:59:59Z\"},"
            + "{\"rewardRef\":\"cpn:CP-201:CT-15OFF\",\"type\":\"coupon\",\"name\":\"15% Off\"}"
            + "],\"rewardCount\":2}";
    private static final String REWARDS_B64 =
            Base64.getEncoder().encodeToString(REWARDS_JSON.getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MockWebServer server;
    private CheckoutSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        session = CheckoutSession.builder()
                .client(BiltNexoTerminalClient.builder()
                        .endpoint(server.url("/nexo").toString())
                        .disableRecoveryOnNetworkError()
                        .build())
                .saleId("POS-LANE-3")
                .poiId("VictaLane-275839164")
                .currency("USD")
                .autoDisplay(false)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private SaleToPOIRequest recordedRequest() throws Exception {
        RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recorded);
        return mapper.readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
                .getSaleToPOIRequest();
    }

    // ─── Terminal-prompted identification ───

    @Test
    void identifyMemberFindsMemberAndTransitionsToIdentified() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\"" + REWARDS_B64 + "\"},"
                        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-LYL-701\"}},"
                        + "\"LoyaltyAccount\":[{"
                        + "\"LoyaltyAccountID\":{\"EntryMode\":[\"Keyed\"],\"IdentificationType\":\"PAN\","
                        + "\"LoyaltyID\":\"98234\"},\"LoyaltyBrand\":\"K-Club\"}]}}}"));

        IdentifyResult result = session.identifyMember().get();

        assertEquals(IdentifyStatus.FOUND, result.getStatus());
        assertEquals("98234", result.getMemberId());
        assertEquals("K-Club", result.getLoyaltyBrand());
        assertEquals(2, result.getRewards().size());
        assertEquals("rwd:RWD-44021", result.getRewards().get(0).getRewardRef());
        assertEquals(RewardType.REWARD, result.getRewards().get(0).getType());
        assertNotNull(result.getRewards().get(0).getExpirationDate());
        assertEquals(RewardType.COUPON, result.getRewards().get(1).getType());

        assertSame(result, session.getMember());
        assertEquals(SessionState.IDENTIFIED, session.getState());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("CardAcquisition", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("Required", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getLoyaltyHandling().toValue());
        assertNotNull(sent.getCardAcquisitionRequest().getSaleData()
                .getSaleTransactionID().getTransactionID());
    }

    @Test
    void identifyMemberHonorsOptions() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        session.identifyMember(IdentifyOptions.builder()
                .forceEntryMode(ForceEntryMode.KEYED)
                .allowedLoyaltyBrand("K-Club")
                .requireMember(false)
                .build()).execute();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Proposed", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getLoyaltyHandling().toValue());
        assertEquals("Keyed", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getForceEntryMode()[0].toValue());
        assertEquals("K-Club", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getAllowedLoyaltyBrand()[0]);
    }

    @Test
    void proposedIdentifyDeclinedByCustomerIsAGuestOutcome() {
        // LoyaltyHandling=Proposed: Success with no LoyaltyAccount means the
        // customer declined the loyalty prompt
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"}}}}"));

        IdentifyResult result = session.identifyMember(IdentifyOptions.builder()
                .requireMember(false)
                .build()).get();

        assertEquals(IdentifyStatus.CANCELLED, result.getStatus());
        assertNull(session.getMember());
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void requiredIdentifySuccessWithoutAccountIsATerminalError() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.identifyMember().get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
    }

    @Test
    void identifyMemberNotFoundIsSuccessWithoutMember() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        IdentifyResult result = session.identifyMember().get();

        assertEquals(IdentifyStatus.NOT_FOUND, result.getStatus());
        assertNull(result.getMemberId());
        assertNull(session.getMember());
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void identifyMemberCancelAndSuspendedMapToStatuses() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Cancel\"}}}}"));
        assertEquals(IdentifyStatus.CANCELLED, session.identifyMember().get().getStatus());

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotAllowed\"}}}}"));
        assertEquals(IdentifyStatus.SUSPENDED, session.identifyMember().get().getStatus());
    }

    @Test
    void reIdentifyWithoutMemberClearsThePreviousMember() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
        session.identifyMember().execute();
        assertNotNull(session.getMember());
        assertEquals(SessionState.IDENTIFIED, session.getState());

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));
        session.identifyMember().execute();

        assertNull(session.getMember(), "a NOT_FOUND re-identify must detach the old member");
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void reIdentifySuspendedClearsMemberButKeepsActiveBasket() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
        session.identifyMember().execute();
        session.addItem(com.bilt.pos.session.basket.BasketItem.of("SKU-1", "Item", 1, "10.00"));

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotAllowed\"}}}}"));
        session.identifyMember().execute();

        assertNull(session.getMember());
        assertEquals(SessionState.ACTIVE, session.getState());

        // emptying the basket now returns to IDLE, not IDENTIFIED
        session.removeItemBySku("SKU-1");
        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void cancelledReIdentifyKeepsThePreviousMember() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
        session.identifyMember().execute();

        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Cancel\"}}}}"));
        session.identifyMember().execute();

        assertNotNull(session.getMember(), "a dismissed prompt must not drop the identified member");
        assertEquals("98234", session.getMember().getMemberId());
        assertEquals(SessionState.IDENTIFIED, session.getState());
    }

    @Test
    void identifyMemberRealFailureGoesToErrorChannel() {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));

        SessionException e = assertThrows(SessionException.class,
                () -> session.identifyMember().get());
        assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
    }

    // ─── POS-driven identification ───

    @Test
    void identifyByPhoneNumberSendsBalanceInquiryAndResolvesMember() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\"" + REWARDS_B64 + "\"},"
                        + "\"LoyaltyAccountStatus\":{\"LoyaltyAccount\":{"
                        + "\"LoyaltyAccountID\":{\"IdentificationType\":\"PAN\",\"LoyaltyID\":\"98234\"},"
                        + "\"LoyaltyBrand\":\"K-Club\"},\"CurrentBalance\":1240}}}}"));

        IdentifyResult result = session.identifyMember(
                MemberIdentifier.phoneNumber("555-867-5309")).get();

        assertEquals(IdentifyStatus.FOUND, result.getStatus());
        assertEquals("98234", result.getMemberId());
        assertEquals(1240, result.getPointBalance());
        assertEquals(2, result.getRewards().size());
        assertEquals(SessionState.IDENTIFIED, session.getState());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("BalanceInquiry", sent.getMessageHeader().getMessageCategory().toValue());
        assertEquals("555-867-5309", sent.getBalanceInquiryRequest()
                .getLoyaltyAccountReq().getLoyaltyAccountID().getLoyaltyID());
        assertEquals("PhoneNumber", sent.getBalanceInquiryRequest()
                .getLoyaltyAccountReq().getLoyaltyAccountID().getIdentificationType().toValue());
        assertEquals("File", sent.getBalanceInquiryRequest()
                .getLoyaltyAccountReq().getLoyaltyAccountID().getEntryMode()[0].toValue());
    }

    @Test
    void keyedByCashierSetsKeyedEntryMode() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                        + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

        session.identifyMember(MemberIdentifier.accountNumber("98234").keyedByCashier()).execute();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("AccountNumber", sent.getBalanceInquiryRequest()
                .getLoyaltyAccountReq().getLoyaltyAccountID().getIdentificationType().toValue());
        assertEquals("Keyed", sent.getBalanceInquiryRequest()
                .getLoyaltyAccountReq().getLoyaltyAccountID().getEntryMode()[0].toValue());
    }

    // ─── Card acquisition ───

    @Test
    void acquireCardMapsCardData() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"PaymentInstrumentData\":{\"PaymentInstrumentType\":\"Card\","
                        + "\"CardData\":{\"MaskedPAN\":\"************1234\",\"PaymentBrand\":\"Visa\","
                        + "\"EntryMode\":[\"Contactless\"],"
                        + "\"SensitiveCardData\":{\"PAN\":\"4111111111111234\",\"ExpiryDate\":\"1227\"},"
                        + "\"PaymentToken\":{\"TokenValue\":\"tok_abc123\"}}}}}}"));

        CardAcquisitionResult result = session.acquireCard().get();

        assertEquals("************1234", result.getMaskedPan());
        assertEquals("1234", result.getTruncatedPan());
        assertEquals("4111111111111234", result.getRawPan());
        assertEquals("Visa", result.getPaymentBrand());
        assertEquals(EntryMode.CONTACTLESS, result.getEntryMode());
        assertEquals("tok_abc123", result.getCardToken());
        assertEquals("1227", result.getExpiryDate());

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Forbidden", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getLoyaltyHandling().toValue());
    }

    @Test
    void acquireCardHonorsForceEntryModes() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"PaymentInstrumentData\":{\"CardData\":{\"MaskedPAN\":\"****1234\"}}}}}"));

        session.acquireCard(CardAcquisitionOptions.builder()
                .forceEntryMode(ForceEntryMode.KEYED)
                .build()).execute();

        SaleToPOIRequest sent = recordedRequest();
        assertEquals("Keyed", sent.getCardAcquisitionRequest()
                .getCardAcquisitionTransaction().getForceEntryMode()[0].toValue());
    }
}
