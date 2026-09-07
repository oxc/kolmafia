package net.sourceforge.kolmafia.request;

import static internal.helpers.Networking.html;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.List;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.session.ContactManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContactListRequestTest {
  @BeforeEach
  public void beforeEach() {
    KoLCharacter.reset("ContactListRequestTest");
  }

  @AfterEach
  public void afterEach() {
    ContactManager.reset();
  }

  @Test
  void canParseNonEmptyContactList() {
    ContactListRequest.parseResponse(
        "account_contactlist.php", html("request/test_account_contact_list.html"));

    assertThat(ContactManager.getMailContacts(), hasSize(2));
    assertThat(
        ContactManager.getMailContacts(),
        containsInAnyOrder("contactlistrequesttest", "torturebot"));
    assertThat(ContactManager.getPlayerId("TortureBot"), equalTo("3495347"));
  }

  @Test
  void canParseEmptyContactList() {
    ContactListRequest.parseResponse(
        "account_contactlist.php", html("request/test_account_contact_list_empty.html"));

    assertThat(ContactManager.getMailContacts(), hasSize(1));
    assertThat(ContactManager.getMailContacts(), containsInAnyOrder("contactlistrequesttest"));
  }

  @Nested
  class IgnoreList {
    @Test
    void canParseNonEmptyIgnoreList() {
      ContactListRequest.parseResponse(
          "account_contactlist.php", html("request/test_account_contact_list.html"));

      assertThat(ContactManager.getIgnoreList(), aMapWithSize(1));
      assertThat(ContactManager.getIgnoreList(), hasEntry("1", "Jick"));
      assertThat(ContactManager.isIgnored("1"), equalTo(true));
      // the page is where a name becomes an id, which is what makes a name enough to ask with
      assertThat(ContactManager.getPlayerId("Jick"), equalTo("1"));
      assertThat(ContactManager.isIgnored(ContactManager.getPlayerId("jick")), equalTo(true));
    }

    @Test
    void canParseEmptyIgnoreList() {
      ContactListRequest.parseResponse(
          "account_contactlist.php", html("request/test_account_contact_list_empty.html"));

      assertThat(ContactManager.getIgnoreList(), anEmptyMap());
      assertThat(ContactManager.isIgnored("1"), equalTo(false));
    }

    @Test
    void doesNotConfuseTheTwoLists() {
      ContactListRequest.parseResponse(
          "account_contactlist.php", html("request/test_account_contact_list.html"));

      assertThat(ContactManager.getMailContacts(), not(contains("jick")));
      assertThat(ContactManager.isIgnored("3495347"), equalTo(false));
      assertThat(
          ContactManager.isIgnored(ContactManager.getPlayerId("TortureBot")), equalTo(false));
    }

    @Test
    void forgetsAnyoneNoLongerOnTheList() {
      ContactListRequest.parseResponse(
          "account_contactlist.php", html("request/test_account_contact_list.html"));
      assertThat(ContactManager.getIgnoreList(), aMapWithSize(1));

      ContactListRequest.parseResponse(
          "account_contactlist.php", html("request/test_account_contact_list_empty.html"));

      assertThat(ContactManager.getIgnoreList(), anEmptyMap());
    }

    @Test
    void addsByName() {
      var request = ContactListRequest.addToIgnoreList("Some Player");

      assertThat(
          request.getURLString(),
          equalTo("account_contactlist.php?action=add&ignore=yes&who=Some+Player&pwd"));
    }

    @Test
    void addsByPlayerIdToo() {
      // the form's field takes either, which is the only way to ignore somebody without minding
      // what they are called this week
      var request = ContactListRequest.addToIgnoreList("1");

      assertThat(
          request.getURLString(),
          equalTo("account_contactlist.php?action=add&ignore=yes&who=1&pwd"));
    }

    @Test
    void removesSeveralPlayersInOneRequest() {
      var request = ContactListRequest.removeFromIgnoreList(List.of("1", "2"));

      assertThat(
          request.getURLString(),
          equalTo("account_contactlist.php?action=remove&which=ignore&pids[]=1&pids[]=2&pwd"));
    }
  }
}
