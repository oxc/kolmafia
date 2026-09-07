package net.sourceforge.kolmafia.request;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.session.ContactManager;

public class ContactListRequest extends GenericRequest {
  private static final Pattern LIST_PATTERN =
      Pattern.compile("<b[^>]*>Contact List</b>.*?</table>");
  private static final Pattern IGNORE_PATTERN =
      Pattern.compile("<b[^>]*>Ignore List</b>.*?</table>");
  // The ignore list quotes its hrefs with apostrophes where the contact list uses quotation marks,
  // so an entry has to be matched with either.
  private static final Pattern ENTRY_PATTERN =
      Pattern.compile("<a href=[\"']showplayer.php\\?who=(\\d+)[\"'].*?<b>(.*?)</b>");

  public ContactListRequest() {
    super("account_contactlist.php");
  }

  /**
   * Adds a player to your ignore list, one at a time, as the form does.
   *
   * @param player the player to start ignoring, by name or by player id
   */
  public static ContactListRequest addToIgnoreList(final String player) {
    ContactListRequest request = new ContactListRequest();
    request.addFormField("action", "add");
    request.addFormField("ignore", "yes");
    request.addFormField("who", player);
    request.addFormField("pwd");
    return request;
  }

  /**
   * Takes players off your ignore list. The form removes by player id and accepts as many as you
   * like in the one request, which is what its "check all" button submits.
   *
   * @param playerIds the players to stop ignoring
   */
  public static ContactListRequest removeFromIgnoreList(final Collection<String> playerIds) {
    ContactListRequest request = new ContactListRequest();
    request.addFormField("action", "remove");
    request.addFormField("which", "ignore");
    for (String playerId : playerIds) {
      request.addFormField("pids[]", playerId, true);
    }
    request.addFormField("pwd");
    return request;
  }

  @Override
  protected boolean retryOnTimeout() {
    return true;
  }

  @Override
  public void run() {
    super.run();
  }

  @Override
  public void processResults() {
    ContactListRequest.parseResponse(this.getURLString(), this.responseText);
  }

  public static final void parseResponse(final String urlString, final String responseText) {
    // Adding and removing both answer with the page itself, so every response says what both lists
    // now hold and neither has to be guessed at.
    ContactManager.clearMailContacts();
    ContactManager.clearIgnoreList();

    ContactManager.addMailContact(KoLCharacter.getUserName(), KoLCharacter.getPlayerId());

    ContactListRequest.parseSection(LIST_PATTERN, responseText, ContactManager::addMailContact);
    ContactListRequest.parseSection(IGNORE_PATTERN, responseText, ContactManager::addToIgnoreList);
  }

  private static void parseSection(
      final Pattern section, final String responseText, final PlayerConsumer consumer) {
    Matcher sectionMatcher = section.matcher(responseText);

    if (!sectionMatcher.find()) {
      return;
    }

    Matcher entryMatcher = ContactListRequest.ENTRY_PATTERN.matcher(sectionMatcher.group());
    while (entryMatcher.find()) {
      consumer.accept(entryMatcher.group(2), entryMatcher.group(1));
    }
  }

  @FunctionalInterface
  private interface PlayerConsumer {
    void accept(String playerName, String playerId);
  }
}
