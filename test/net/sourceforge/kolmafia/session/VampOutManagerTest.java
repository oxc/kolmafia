package net.sourceforge.kolmafia.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.preferences.Preferences;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VampOutManagerTest {
  @BeforeAll
  public static void beforeAll() {
    KoLCharacter.reset("vamp out user");
  }

  @BeforeEach
  public void beforeEach() {
    Preferences.reset("vamp out user");
  }

  private static String firstPage(final String... options) {
    StringBuilder buffer = new StringBuilder("Finally, the sun has set.");
    for (int i = 0; i < options.length; ++i) {
      buffer
          .append("<form><input type=hidden name=option value=")
          .append(i + 1)
          .append("><input class=button type=submit value=\"")
          .append(options[i])
          .append("\"></form>\n");
    }
    return buffer.toString();
  }

  @Test
  public void thatTheMasqueradeIsFoundBehindEscapedApostrophes() {
    // KoL escapes the apostrophes in the option labels, so matching them literally found neither
    // Vlad's nor Isabella's and took the wrong door.
    String responseText =
        firstPage("Visit Vlad&#039;s Boutique", "Visit Isabella&#039;s", "Visit The Masquerade");

    // Goal 13 is "your own black heart", behind The Masquerade
    assertEquals("3", VampOutManager.autoVampOut(13, 0, responseText));
    assertFalse(Preferences.getBoolean("_interviewVlad"));
    assertFalse(Preferences.getBoolean("_interviewIsabella"));
    assertFalse(Preferences.getBoolean("_interviewMasquerade"));
  }

  @Test
  public void thatAVisitedDoorShiftsTheOptionIndex() {
    String responseText = firstPage("Visit Isabella&#039;s", "Visit The Masquerade");

    // Goal 13 is "your own black heart", behind The Masquerade, now the second option
    assertEquals("2", VampOutManager.autoVampOut(13, 0, responseText));
    // Goal 1 is "Mistified", behind Vlad's Boutique, which has already been visited
    assertEquals("0", VampOutManager.autoVampOut(1, 0, responseText));
  }
}
