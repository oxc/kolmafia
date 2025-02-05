package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.QuestLogRequest;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;
import net.sourceforge.kolmafia.session.InventoryManager;

public class BlackMarketRequest extends CoinMasterRequest {
  public static final String master = "The Black Market";

  public static final AdventureResult TOKEN = ItemPool.get(ItemPool.PRICELESS_DIAMOND, 1);
  private static final Pattern TOKEN_PATTERN = Pattern.compile("<td>([\\d,]+) priceless diamond");

  public static final CoinmasterData BLACK_MARKET =
      new CoinmasterData(master, "blackmarket", BlackMarketRequest.class)
          .withToken("priceless diamond")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(TOKEN)
          .withShopRowFields(master, "blackmarket")
          .withCanBuyItem(BlackMarketRequest::canBuyItem)
          .withAccessible(BlackMarketRequest::accessible);

  private static Boolean canBuyItem(final Integer itemId) {
    AdventureResult item = ItemPool.get(itemId);
    return switch (itemId) {
      case ItemPool.ZEPPELIN_TICKET -> InventoryManager.getCount(item) == 0;
      default -> item.getCount(BLACK_MARKET.getBuyItems()) > 0;
    };
  }

  public BlackMarketRequest() {
    super(BLACK_MARKET);
  }

  public BlackMarketRequest(final boolean buying, final AdventureResult[] attachments) {
    super(BLACK_MARKET, buying, attachments);
  }

  public BlackMarketRequest(final boolean buying, final AdventureResult attachment) {
    super(BLACK_MARKET, buying, attachment);
  }

  public BlackMarketRequest(final boolean buying, final int itemId, final int quantity) {
    super(BLACK_MARKET, buying, itemId, quantity);
  }

  @Override
  public void processResults() {
    parseResponse(this.getURLString(), responseText);
  }

  public static void parseResponse(final String location, final String responseText) {
    if (!location.contains("whichshop=blackmarket")) {
      return;
    }

    CoinmasterData data = BLACK_MARKET;
    int itemId = CoinMasterRequest.extractItemId(data, location);

    if (itemId == -1) {
      // Purchase for Meat or a simple visit
      CoinMasterRequest.parseBalance(data, responseText);
      return;
    }

    CoinMasterRequest.parseResponse(data, location, responseText);
  }

  public static String accessible() {
    if (!QuestLogRequest.isBlackMarketAvailable()) {
      return "The Black Market is not currently available";
    }
    return null;
  }
}
