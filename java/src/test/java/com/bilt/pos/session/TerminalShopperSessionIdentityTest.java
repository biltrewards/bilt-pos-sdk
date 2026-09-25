package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

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
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.identity.RewardType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalShopperSessionIdentityTest {

  private static final String REWARDS_JSON =
      "{\"rewards\":["
          + "{\"rewardRef\":\"rwd:RWD-44021\",\"type\":\"reward\",\"name\":\"$10 Off Purchase\","
          + "\"expirationDate\":\"2026-05-15T23:59:59Z\"},"
          + "{\"rewardRef\":\"cpn:CP-201:CT-15OFF\",\"type\":\"coupon\",\"name\":\"15% Off\"}"
          + "],\"rewardCount\":2}";
  private static final String REWARDS_B64 =
      Base64.getEncoder().encodeToString(REWARDS_JSON.getBytes(StandardCharsets.UTF_8));

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MockWebServer server;
  private TerminalShopperSession session;
  private final List<Member> memberChanges = new CopyOnWriteArrayList<>();
  private final List<SessionError> backgroundErrors = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    session =
        TerminalShopperSession.builder()
            .client(
                BiltNexoTerminalClient.builder()
                    .endpoint(server.url("/nexo").toString())
                    .disableRecoveryOnNetworkError()
                    .build())
            .saleId("POS-LANE-3")
            .poiId("VictaLane-275839164")
            .currency("USD")
            .autoDisplay(false)
            .onMemberChanged(memberChanges::add)
            .onBackgroundError(backgroundErrors::add)
            .start()
            .get();
    server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private SaleToPOIRequest recordedRequest() throws Exception {
    RecordedRequest recorded = server.takeRequest(5, TimeUnit.SECONDS);
    assertNotNull(recorded);
    return mapper
        .readValue(recorded.getBody().readUtf8(), NexoTerminalAPI.class)
        .getSaleToPOIRequest();
  }

  // ─── Terminal-prompted identification ───

  @Test
  void identifyOutcomeArrivingAfterAbortIsStillDelivered() throws Exception {
    String found =
        "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
            + "\"Response\":{\"Result\":\"Success\"},"
            + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"},"
            + "\"LoyaltyBrand\":\"K-Club\"}]}}}";
    CountDownLatch identifyOnTheWire = new CountDownLatch(1);
    CountDownLatch aborted = new CountDownLatch(1);
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            if (request.getBody().readUtf8().contains("CardAcquisitionRequest")) {
              identifyOnTheWire.countDown();
              // hold the FOUND response until abort() has fired
              aborted.await(5, TimeUnit.SECONDS);
              return new MockResponse().setBody(found);
            }
            return new MockResponse(); // the AbortRequest, best-effort
          }
        });

    AtomicReference<IdentifyResult> delivered = new AtomicReference<>();
    AtomicReference<SessionException> failure = new AtomicReference<>();
    Thread register =
        new Thread(
            () -> {
              try {
                delivered.set(session.identifyMember().get());
              } catch (SessionException e) {
                failure.set(e);
              }
            });
    register.start();
    assertTrue(identifyOnTheWire.await(5, TimeUnit.SECONDS));

    session.abort().executeSync();
    aborted.countDown();
    register.join(5_000);
    assertFalse(register.isAlive());

    // abort is operation-scoped and the terminal answered FOUND before
    // processing it: the outcome is real and is delivered — the
    // checkout continues with the member attached
    assertNull(failure.get());
    assertNotNull(delivered.get());
    assertEquals(IdentifyStatus.FOUND, delivered.get().getStatus());
    assertNotNull(session.getMember());
  }

  @Test
  void identifyMemberFindsMemberAndTransitionsToIdentified() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
                    + REWARDS_B64
                    + "\"},"
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

    assertEquals("98234", session.getMember().getMemberId());
    assertEquals(Member.resolved(result), session.member());
    assertEquals(List.of(Member.resolved(result)), memberChanges);

    SaleToPOIRequest sent = recordedRequest();
    assertEquals("CardAcquisition", sent.getMessageHeader().getMessageCategory().toValue());
    assertEquals(
        "Required",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getLoyaltyHandling()
            .toValue());
    assertNotNull(
        sent.getCardAcquisitionRequest().getSaleData().getSaleTransactionID().getTransactionID());
  }

  @Test
  void identifyMemberHonorsOptions() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

    session
        .identifyMember(
            IdentifyOptions.builder()
                .forceEntryMode(ForceEntryMode.KEYED)
                .allowedLoyaltyBrand("K-Club")
                .requireMember(false)
                .build())
        .executeSync();

    SaleToPOIRequest sent = recordedRequest();
    assertEquals(
        "Proposed",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getLoyaltyHandling()
            .toValue());
    assertEquals(
        "Keyed",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getForceEntryMode()[0]
            .toValue());
    assertEquals(
        "K-Club",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getAllowedLoyaltyBrand()[0]);
  }

  @Test
  void proposedIdentifyDeclinedByCustomerIsAGuestOutcome() {
    // LoyaltyHandling=Proposed: Success with no LoyaltyAccount means the
    // customer declined the loyalty prompt
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"}}}}"));

    IdentifyResult result =
        session.identifyMember(IdentifyOptions.builder().requireMember(false).build()).get();

    assertEquals(IdentifyStatus.CANCELLED, result.getStatus());
    assertNull(session.getMember());
  }

  @Test
  void requiredIdentifySuccessWithoutAccountIsATerminalError() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"}}}}"));

    SessionException e = assertThrows(SessionException.class, () -> session.identifyMember().get());
    assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
  }

  @Test
  void identifyMemberNotFoundIsSuccessWithoutMember() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

    IdentifyResult result = session.identifyMember().get();

    assertEquals(IdentifyStatus.NOT_FOUND, result.getStatus());
    assertNull(result.getMemberId());
    assertNull(session.getMember());
  }

  @Test
  void identifyMemberCancelAndSuspendedMapToStatuses() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Cancel\"}}}}"));
    assertEquals(IdentifyStatus.CANCELLED, session.identifyMember().get().getStatus());

    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotAllowed\"}}}}"));
    assertEquals(IdentifyStatus.SUSPENDED, session.identifyMember().get().getStatus());
  }

  @Test
  void reIdentifyWithoutMemberClearsThePreviousMember() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
    session.identifyMember().executeSync();
    assertNotNull(session.getMember());

    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));
    session.identifyMember().executeSync();

    assertNull(session.getMember(), "a NOT_FOUND re-identify must detach the old member");
  }

  @Test
  void reIdentifySuspendedClearsMemberButKeepsActiveBasket() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
    session.identifyMember().executeSync();
    session
        .basket()
        .addItem(
            com.bilt.pos.session.basket.BasketItem.sale(
                "SKU-1", "Item", 1, new BigDecimal("10.00")));

    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotAllowed\"}}}}"));
    session.identifyMember().executeSync();

    assertNull(session.getMember());

    session.basket().removeItemBySku("SKU-1");
    assertTrue(session.basket().snapshot().isEmpty());
  }

  @Test
  void cancelledReIdentifyKeepsThePreviousMember() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"LoyaltyAccount\":[{\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}]}}}"));
    session.identifyMember().executeSync();

    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Cancel\"}}}}"));
    session.identifyMember().executeSync();

    assertNotNull(session.getMember(), "a dismissed prompt must not drop the identified member");
    assertEquals("98234", session.getMember().getMemberId());
  }

  @Test
  void identifyMemberRealFailureGoesToErrorChannel() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));

    SessionException e = assertThrows(SessionException.class, () -> session.identifyMember().get());
    assertEquals(SessionErrorCode.TERMINAL_ERROR, e.getError().getCode());
  }

  @Test
  void posDrivenLookupFailuresAreLabelledAsBalanceInquiry() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"UnavailableService\"}}}}"));

    SessionException e =
        assertThrows(
            SessionException.class,
            () -> session.identifyMember(Member.idResolver().phone("555-867-5309")).get());
    assertTrue(
        e.getError().getMessage().startsWith("BalanceInquiry"),
        "the error must name the operation that failed: " + e.getError().getMessage());
  }

  // ─── POS-driven identification ───

  @Test
  void identifyByPhoneNumberSendsBalanceInquiryAndResolvesMember() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
                    + REWARDS_B64
                    + "\"},"
                    + "\"LoyaltyAccountStatus\":{\"LoyaltyAccount\":{"
                    + "\"LoyaltyAccountID\":{\"IdentificationType\":\"PAN\",\"LoyaltyID\":\"98234\"},"
                    + "\"LoyaltyBrand\":\"K-Club\"},\"CurrentBalance\":1240}}}}"));

    IdentifyResult result = session.identifyMember(Member.idResolver().phone("555-867-5309")).get();

    assertEquals(IdentifyStatus.FOUND, result.getStatus());
    assertEquals("98234", result.getMemberId());
    assertEquals(1240, result.getPointBalance());
    assertEquals(2, result.getRewards().size());
    assertEquals("98234", session.member().memberId());
    assertEquals(1240, session.member().pointBalance());

    SaleToPOIRequest sent = recordedRequest();
    assertEquals("BalanceInquiry", sent.getMessageHeader().getMessageCategory().toValue());
    assertEquals(
        "555-867-5309",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getLoyaltyID());
    assertEquals(
        "PhoneNumber",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getIdentificationType()
            .toValue());
    assertEquals(
        "File",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getEntryMode()[0]
            .toValue());
  }

  @Test
  void keyedByCashierSetsKeyedEntryMode() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

    session.identifyMember(Member.idResolver().accountId("98234").keyedByCashier()).executeSync();

    SaleToPOIRequest sent = recordedRequest();
    assertEquals(
        "AccountNumber",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getIdentificationType()
            .toValue());
    assertEquals(
        "Keyed",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getEntryMode()[0]
            .toValue());
  }

  @Test
  void identifyMemberRefusesAResolvedMember() {
    assertThrows(
        IllegalArgumentException.class, () -> session.identifyMember(Member.id("mbr_8f2a")));
  }

  @Test
  void identifyMemberByEmailIsUnsupportedOnTheTerminal() {
    SessionException e =
        assertThrows(
            SessionException.class,
            () -> session.identifyMember(Member.idResolver().email("shopper@example.com")).get());
    assertEquals(SessionErrorCode.UNSUPPORTED, e.getError().getCode());
    assertEquals(1, server.getRequestCount(), "nothing but the start signal reached the terminal");
  }

  // ─── POS-provided member ───

  @Test
  void resolvedMemberAttachesImmediatelyWithoutARoundtrip() {
    session.member(Member.id("mbr_8f2a"));

    assertEquals(Member.id("mbr_8f2a"), session.member());
    assertTrue(session.member().isResolved());
    assertEquals("mbr_8f2a", session.getMember().getMemberId());
    assertEquals(List.of(Member.id("mbr_8f2a")), memberChanges);
    assertEquals(1, server.getRequestCount(), "only the start signal went to the terminal");

    session.member(null);
    assertNull(session.member());
    assertNull(session.getMember());
    assertEquals(2, memberChanges.size());
    assertNull(memberChanges.get(1));
  }

  @Test
  void pendingMemberIsResolvedWithTheSameBalanceInquiryAsAnExplicitLookup() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\",\"AdditionalResponse\":\""
                    + REWARDS_B64
                    + "\"},"
                    + "\"LoyaltyAccountStatus\":{\"LoyaltyAccount\":{"
                    + "\"LoyaltyAccountID\":{\"IdentificationType\":\"PAN\",\"LoyaltyID\":\"98234\"},"
                    + "\"LoyaltyBrand\":\"K-Club\"},\"CurrentBalance\":1240}}}}"));

    Member pending = Member.idResolver().phone("555-867-5309");
    session.member(pending);

    assertSame(pending, session.member(), "attached as pending straight away");
    assertFalse(session.member().isResolved());
    assertNull(session.getMember(), "a pending member is a guest until it resolves");

    SaleToPOIRequest sent = recordedRequest();
    assertEquals("BalanceInquiry", sent.getMessageHeader().getMessageCategory().toValue());
    assertEquals(
        "555-867-5309",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getLoyaltyID());
    assertEquals(
        "PhoneNumber",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getIdentificationType()
            .toValue());
    assertEquals(
        "File",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getEntryMode()[0]
            .toValue());

    Member resolved = awaitResolution();
    assertTrue(resolved.isResolved());
    assertEquals("98234", resolved.memberId());
    assertEquals("K-Club", resolved.loyaltyBrand());
    assertEquals(1240, resolved.pointBalance());
    assertEquals(2, resolved.rewards().size());
    assertEquals(resolved, session.member());
    assertEquals("98234", session.getMember().getMemberId());
    assertEquals(List.of(pending, resolved), memberChanges);
    assertTrue(backgroundErrors.isEmpty());
  }

  @Test
  void pendingKeyedAccountIdSendsKeyedEntryMode() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                    + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"NotFound\"}}}}"));

    session.member(Member.idResolver().accountId("98234").keyedByCashier());

    SaleToPOIRequest sent = recordedRequest();
    assertEquals(
        "AccountNumber",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getIdentificationType()
            .toValue());
    assertEquals(
        "Keyed",
        sent.getBalanceInquiryRequest()
            .getLoyaltyAccountReq()
            .getLoyaltyAccountID()
            .getEntryMode()[0]
            .toValue());

    // nobody has that account: the pending member is cleared, and the
    // register hears about it
    assertNull(awaitResolution());
    assertNull(session.member());
    assertEquals(2, memberChanges.size());
    assertNull(memberChanges.get(1));
  }

  @Test
  void pendingEmailReportsABackgroundErrorAndStaysPending() throws Exception {
    Member pending = Member.idResolver().email("shopper@example.com");
    session.member(pending);

    SessionError error = awaitBackgroundError();
    assertEquals(SessionErrorCode.UNSUPPORTED, error.getCode());
    assertTrue(error.getMessage().contains("EMAIL"), error.getMessage());
    assertSame(pending, session.member(), "the member stays pending");
    assertFalse(session.member().isResolved());
    assertNull(session.getMember());
    assertEquals(List.of(pending), memberChanges, "no resolution to announce");
    assertEquals(1, server.getRequestCount(), "nothing reached the terminal");
  }

  @Test
  void aMemberAttachedDuringTheLookupIsNotOverwrittenByItsOutcome() throws Exception {
    CountDownLatch lookupOnTheWire = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            if (request.getBody().clone().readUtf8().contains("\"AdminRequest\"")) {
              return new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK);
            }
            lookupOnTheWire.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new MockResponse()
                .setBody(
                    "{\"SaleToPOIResponse\":{\"BalanceInquiryResponse\":{"
                        + "\"Response\":{\"Result\":\"Success\"},"
                        + "\"LoyaltyAccountStatus\":{\"LoyaltyAccount\":{"
                        + "\"LoyaltyAccountID\":{\"LoyaltyID\":\"98234\"}}}}}}");
          }
        });

    session.member(Member.idResolver().phone("555-867-5309"));
    assertTrue(lookupOnTheWire.await(5, TimeUnit.SECONDS));
    session.member(Member.id("mbr_direct"));
    release.countDown();

    // end() queues behind the lookup on the lane, so once it returns the
    // lookup has completed — and been discarded
    session.end().executeSync();

    assertEquals(Member.id("mbr_direct"), session.member(), "latest attached member wins");
    assertEquals(2, memberChanges.size());
    assertEquals(Member.id("mbr_direct"), memberChanges.get(1));
  }

  private Member awaitResolution() throws Exception {
    long deadline = System.currentTimeMillis() + 5_000;
    while (memberChanges.size() < 2 && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertEquals(2, memberChanges.size(), "the resolution must be announced: " + memberChanges);
    return memberChanges.get(1);
  }

  private SessionError awaitBackgroundError() throws Exception {
    long deadline = System.currentTimeMillis() + 5_000;
    while (backgroundErrors.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertEquals(1, backgroundErrors.size(), "one background error expected: " + backgroundErrors);
    return backgroundErrors.get(0);
  }

  // ─── Card acquisition ───

  @Test
  void acquireCardMapsCardData() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
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
    assertEquals(
        "Forbidden",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getLoyaltyHandling()
            .toValue());
  }

  @Test
  void acquireCardHonorsForceEntryModes() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"SaleToPOIResponse\":{\"CardAcquisitionResponse\":{"
                    + "\"Response\":{\"Result\":\"Success\"},"
                    + "\"PaymentInstrumentData\":{\"CardData\":{\"MaskedPAN\":\"****1234\"}}}}}"));

    session
        .acquireCard(CardAcquisitionOptions.builder().forceEntryMode(ForceEntryMode.KEYED).build())
        .executeSync();

    SaleToPOIRequest sent = recordedRequest();
    assertEquals(
        "Keyed",
        sent.getCardAcquisitionRequest()
            .getCardAcquisitionTransaction()
            .getForceEntryMode()[0]
            .toValue());
  }
}
