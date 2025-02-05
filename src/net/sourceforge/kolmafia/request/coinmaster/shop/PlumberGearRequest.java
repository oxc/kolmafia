package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.GenericRequest;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;

public class PlumberGearRequest extends CoinMasterRequest {
  public static final String master = "Mushroom District Gear Shop";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("([\\d,]+) coin");
  public static final AdventureResult COIN = ItemPool.get(ItemPool.COIN, 1);

  public static final CoinmasterData PLUMBER_GEAR =
      new CoinmasterData(master, "mariogear", PlumberGearRequest.class)
          .withToken("coin")
          .withTokenTest("no coins")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(COIN)
          .withShopRowFields(master, "mariogear")
          .withAccessible(PlumberGearRequest::accessible);

  public PlumberGearRequest() {
    super(PLUMBER_GEAR);
  }

  public PlumberGearRequest(final boolean buying, final AdventureResult[] attachments) {
    super(PLUMBER_GEAR, buying, attachments);
  }

  public PlumberGearRequest(final boolean buying, final AdventureResult attachment) {
    super(PLUMBER_GEAR, buying, attachment);
  }

  public PlumberGearRequest(final boolean buying, final int itemId, final int quantity) {
    super(PLUMBER_GEAR, buying, itemId, quantity);
  }

  @Override
  public void processResults() {
    parseResponse(this.getURLString(), this.responseText);
  }

  public static void parseResponse(final String location, final String responseText) {
    CoinmasterData data = PLUMBER_GEAR;
    String action = GenericRequest.getAction(location);
    if (action == null) {
      if (location.contains("whichshop=mariogear")) {
        // Parse current coin balances
        CoinMasterRequest.parseBalance(data, responseText);
      }

      return;
    }

    CoinMasterRequest.parseResponse(data, location, responseText);
  }

  public static String accessible() {
    if (!KoLCharacter.isPlumber()) {
      return "You are not a plumber.";
    }
    return null;
  }
}
