package com.bilt.pos.session.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  void idIsResolvedWithNothingButTheMemberId() {
    Member member = Member.id("mbr_8f2a");

    assertTrue(member.isResolved());
    assertEquals("mbr_8f2a", member.memberId());
    assertNull(member.resolver());
    assertEquals(IdentifyStatus.FOUND, member.status());
    assertNull(member.loyaltyBrand());
    assertTrue(member.rewards().isEmpty());
    assertEquals(0, member.pointBalance());
    assertEquals("Member{id=mbr_8f2a}", member.toString());
  }

  @Test
  void idRejectsMissingOrEmptyIds() {
    assertThrows(NullPointerException.class, () -> Member.id(null));
    assertThrows(IllegalArgumentException.class, () -> Member.id(""));
  }

  @Test
  void resolvedCarriesWhatTheTerminalReported() {
    Reward reward =
        new Reward(
            "rwd:RWD-44021", RewardType.REWARD, "$10 Off", Instant.parse("2026-05-15T23:59:59Z"));
    IdentifyResult found = IdentifyResult.found("98234", "K-Club", List.of(reward), 1240);

    Member member = Member.resolved(found);

    assertTrue(member.isResolved());
    assertEquals("98234", member.memberId());
    assertEquals("K-Club", member.loyaltyBrand());
    assertEquals(List.of(reward), member.rewards());
    assertEquals(1240, member.pointBalance());
    assertEquals(IdentifyStatus.FOUND, member.status());
    assertEquals("Member{id=98234, brand=K-Club, points=1240, rewards=1}", member.toString());
    assertThrows(UnsupportedOperationException.class, () -> member.rewards().add(reward));
  }

  @Test
  void resolvedRequiresAFoundResult() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Member.resolved(IdentifyResult.withoutMember(IdentifyStatus.NOT_FOUND)));
    assertThrows(NullPointerException.class, () -> Member.resolved(null));
  }

  @Test
  void idResolverFactoriesProducePendingMembers() {
    Member byAccount = Member.idResolver().accountId("4823");
    Member byPhone = Member.idResolver().phone("+12015550123");
    Member byEmail = Member.idResolver().email("shopper@example.com");
    Member byCustom = Member.idResolver().custom("retailer-card", "99-1234");

    for (Member pending : List.of(byAccount, byPhone, byEmail, byCustom)) {
      assertFalse(pending.isResolved());
      assertNull(pending.memberId());
      assertNull(pending.status());
      assertNotNull(pending.resolver());
      assertFalse(pending.resolver().keyedByCashier());
      assertTrue(pending.rewards().isEmpty());
    }
    assertEquals(MemberIdResolver.Type.ACCOUNT_ID, byAccount.resolver().type());
    assertEquals("4823", byAccount.resolver().value());
    assertNull(byAccount.resolver().customType());
    assertEquals(MemberIdResolver.Type.PHONE, byPhone.resolver().type());
    assertEquals("+12015550123", byPhone.resolver().value());
    assertEquals(MemberIdResolver.Type.EMAIL, byEmail.resolver().type());
    assertEquals("shopper@example.com", byEmail.resolver().value());
    assertEquals(MemberIdResolver.Type.CUSTOM, byCustom.resolver().type());
    assertEquals("retailer-card", byCustom.resolver().customType());
    assertEquals("99-1234", byCustom.resolver().value());
  }

  @Test
  void idResolverFactoriesRejectMissingValues() {
    assertThrows(NullPointerException.class, () -> Member.idResolver().phone(null));
    assertThrows(IllegalArgumentException.class, () -> Member.idResolver().phone(""));
    assertThrows(NullPointerException.class, () -> Member.idResolver().custom(null, "99-1234"));
    assertThrows(IllegalArgumentException.class, () -> Member.idResolver().custom("", "99-1234"));
    assertThrows(
        IllegalArgumentException.class, () -> Member.idResolver().custom("retailer-card", ""));
  }

  @Test
  void keyedByCashierIsACopyOnThePendingMember() {
    Member onFile = Member.idResolver().phone("+12015550123");
    Member keyed = onFile.keyedByCashier();

    assertFalse(onFile.resolver().keyedByCashier(), "the original is untouched");
    assertTrue(keyed.resolver().keyedByCashier());
    assertEquals(onFile.resolver().type(), keyed.resolver().type());
    assertEquals(onFile.resolver().value(), keyed.resolver().value());
    assertNotEquals(onFile, keyed);
    assertEquals(keyed, Member.idResolver().phone("+12015550123").keyedByCashier());
    assertThrows(IllegalStateException.class, () -> Member.id("mbr_8f2a").keyedByCashier());
  }

  @Test
  void toStringRedactsPhoneAndEmailButNotAccountIds() {
    assertEquals(
        "Member{pending PHONE ********0123}", Member.idResolver().phone("+12015550123").toString());
    assertEquals(
        "Member{pending EMAIL ***************.com}",
        Member.idResolver().email("shopper@example.com").toString());
    assertEquals(
        "Member{pending PHONE *****09, keyed by cashier}",
        Member.idResolver().phone("8675309").keyedByCashier().toString());
    assertEquals("Member{pending PHONE ***}", Member.idResolver().phone("911").toString());
    assertEquals(
        "Member{pending ACCOUNT_ID 4823}", Member.idResolver().accountId("4823").toString());
    assertEquals(
        "Member{pending CUSTOM(retailer-card) 99-1234}",
        Member.idResolver().custom("retailer-card", "99-1234").toString());
  }

  @Test
  void valueSemantics() {
    assertEquals(Member.id("mbr_8f2a"), Member.id("mbr_8f2a"));
    assertEquals(Member.id("mbr_8f2a").hashCode(), Member.id("mbr_8f2a").hashCode());
    assertNotEquals(Member.id("mbr_8f2a"), Member.id("mbr_other"));
    assertNotEquals(Member.id("4823"), Member.idResolver().accountId("4823"));

    assertEquals(
        Member.idResolver().phone("+12015550123"), Member.idResolver().phone("+12015550123"));
    assertNotEquals(
        Member.idResolver().phone("+12015550123"), Member.idResolver().email("+12015550123"));
    assertNotEquals(
        Member.idResolver().custom("a", "99-1234"), Member.idResolver().custom("b", "99-1234"));

    IdentifyResult found =
        IdentifyResult.found(
            "98234",
            "K-Club",
            List.of(new Reward("rwd:RWD-44021", RewardType.REWARD, "$10 Off", null)),
            1240);
    assertEquals(Member.resolved(found), Member.resolved(found));
    assertNotEquals(
        Member.resolved(found), Member.id("98234"), "terminal detail is part of the value");
  }
}
