package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.GenericRequest;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;

public class LTTRequest extends CoinMasterRequest {
  public static final String master = "LT&T Gift Shop";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<td>([\\d,]+) buffalo dime");
  public static final AdventureResult COIN = ItemPool.get(ItemPool.BUFFALO_DIME, 1);

  public static final CoinmasterData LTT =
      new CoinmasterData(master, "LT&T Gift Shop", LTTRequest.class)
          .withToken("buffalo dime")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(COIN)
          .withShopRowFields(master, "ltt")
          .withAccessible(LTTRequest::accessible);

  public LTTRequest() {
    super(LTT);
  }

  public LTTRequest(final boolean buying, final AdventureResult[] attachments) {
    super(LTT, buying, attachments);
  }

  public LTTRequest(final boolean buying, final AdventureResult attachment) {
    super(LTT, buying, attachment);
  }

  public LTTRequest(final boolean buying, final int itemId, final int quantity) {
    super(LTT, buying, itemId, quantity);
  }

  @Override
  public void processResults() {
    parseResponse(this.getURLString(), this.responseText);
  }

  public static void parseResponse(final String urlString, final String responseText) {
    if (!urlString.contains("whichshop=ltt")) {
      return;
    }

    String action = GenericRequest.getAction(urlString);
    if (action != null) {
      CoinMasterRequest.parseResponse(LTT, urlString, responseText);
      return;
    }

    // Parse current coin balances
    CoinMasterRequest.parseBalance(LTT, responseText);
  }

  public static String accessible() {
    // *** Finish this.
    return null;
  }
}
