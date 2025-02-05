package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.GenericRequest;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;

public class BoutiqueRequest extends CoinMasterRequest {
  public static final String master = "Paul's Boutique";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<td>([\\d,]+) odd silver coin");
  public static final AdventureResult COIN = ItemPool.get(ItemPool.ODD_SILVER_COIN, 1);

  public static final CoinmasterData BOUTIQUE =
      new CoinmasterData(master, "boutique", BoutiqueRequest.class)
          .withToken("odd silver coin")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(COIN)
          .withShopRowFields(master, "cindy")
          .withAccessible(BoutiqueRequest::accessible);

  public BoutiqueRequest() {
    super(BOUTIQUE);
  }

  public BoutiqueRequest(final boolean buying, final AdventureResult[] attachments) {
    super(BOUTIQUE, buying, attachments);
  }

  public BoutiqueRequest(final boolean buying, final AdventureResult attachment) {
    super(BOUTIQUE, buying, attachment);
  }

  public BoutiqueRequest(final boolean buying, final int itemId, final int quantity) {
    super(BOUTIQUE, buying, itemId, quantity);
  }

  @Override
  public void processResults() {
    parseResponse(this.getURLString(), this.responseText);
  }

  public static void parseResponse(final String urlString, final String responseText) {
    if (!urlString.contains("whichshop=cindy")) {
      return;
    }

    CoinmasterData data = BOUTIQUE;

    String action = GenericRequest.getAction(urlString);
    if (action != null) {
      CoinMasterRequest.parseResponse(data, urlString, responseText);
      return;
    }

    // Parse current coin balances
    CoinMasterRequest.parseBalance(data, responseText);
  }

  public static String accessible() {
    int coin = COIN.getCount(KoLConstants.inventory);
    if (coin == 0) {
      return "You don't have an odd silver coin.";
    }
    return null;
  }
}
