package net.sourceforge.kolmafia.request;

import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.sourceforge.kolmafia.persistence.SkillDatabase;
import net.sourceforge.kolmafia.session.ContactManager;
import net.sourceforge.kolmafia.utilities.HTMLParserUtils;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.w3c.dom.NodeList;

public class CastBuffRequest extends GenericRequest {

  private static final Pattern PATTERN_ADV = Pattern.compile("(\\d+)\\s+Adventures\\)");

  private static final HtmlCleaner cleaner = HTMLParserUtils.configureDefaultParser();
  private static final DomSerializer domSerializer = new DomSerializer(cleaner.getProperties());

  private final int skillId;
  private final int buffCount;
  private final String skillName;
  private final String target;
  private final UseSkillRequest skillRequest;

  private int turnsCast = -2;
  private String resultText;

  private CastBuffRequest(final int skillId, final String target, int buffCount) {
    super(chooseURL(skillId));
    this.skillId = skillId;
    this.buffCount = buffCount;
    this.skillRequest = UseSkillRequest.getInstance(skillId, target, buffCount);

    this.skillName = skillRequest.getSkillName();
    var isBuff = SkillDatabase.isBuff(this.skillId);
    if (!isBuff) {
      throw new IllegalArgumentException("Skill " + this.skillName + " is not a buff.");
    }

    this.target = ContactManager.getPlayerName(target);

    this.addFormField("action", "Skillz");
    this.addFormField("whichskill", String.valueOf(this.skillId));
    this.addFormField("ajax", "1");
    this.addFormField("targetplayer", ContactManager.getPlayerId(target));
    this.addFormField("quantity", String.valueOf(buffCount));
    this.addFormField("pwd");
    this.constructURLString(getFullURLString(), false);
  }

  private static String chooseURL(final int skillId) {
    return SkillDatabase.isBookshelfSkill(skillId) ? "campground.php" : "runskillz.php";
  }

  public static CastBuffRequest getInstance(int skillId, String target, int buffCount) {
    return new CastBuffRequest(skillId, target, buffCount);
  }

  @Override
  public void processResults() {
    super.processResults();

    this.resultText = parseText(this.responseText);

    var turnsMatcher = PATTERN_ADV.matcher(this.responseText);
    if (turnsMatcher.find()) {
      turnsCast = Integer.parseInt(turnsMatcher.group(1));
    } else {
      turnsCast = -1;
    }
  }

  private String parseText(String responseText) {
    try {
      var doc = domSerializer.createDOM(cleaner.clean(responseText));
      var textNodes =
          (NodeList)
              XPathFactory.newInstance()
                  .newXPath()
                  .evaluate(
                      """
                        .//text()[
                          parent::*[
                            local-name() != "script" and
                            local-name() != "a" and
                            local-name() != "option" and
                            local-name() != "style" and
                            not (ancestor::td[@bgcolor="blue"])
                          ]
                        ]
                      """,
                      doc,
                      XPathConstants.NODESET);

      var result = new StringBuilder();
      for (int i = 0; i < textNodes.getLength(); i++) {
        var text = textNodes.item(i).getNodeValue();
        if (text != null && !text.isBlank()) {
          if (!result.isEmpty()) {
            result.append(" ");
          }
          result.append(text);
        }
      }

      return result.toString();
    } catch (XPathExpressionException | ParserConfigurationException e) {
      // Our xpath selector is bad; this build shouldn't be released at all
      e.printStackTrace();
      throw new RuntimeException("Bad XPath selector");
    }
  }

  public String getResultText() {
    return resultText;
  }

  public int getTurnsCast() {
    return turnsCast;
  }
}
