package net.sourceforge.kolmafia.oxc;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.textui.MessageChannel;
import net.sourceforge.kolmafia.textui.MessageChannel.Message;
import net.sourceforge.kolmafia.utilities.StringUtilities;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class BuffooneryHttpClient {

  public static final BuffooneryHttpClient INSTANCE = new BuffooneryHttpClient();

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private static final AtomicInteger nextWebsocketHandle = new AtomicInteger(1);

  private final OkHttpClient client;

  private final HttpLoggingInterceptor loggingInterceptor;

  private final Map<Integer, WebSocket> openWebSockets = new ConcurrentHashMap<>();

  private BuffooneryHttpClient() {
    loggingInterceptor =
        new HttpLoggingInterceptor(
            s -> {
              s = StringUtilities.globalStringReplace(s, "<", "&lt;");
              if (s.startsWith("-->")) {
                RequestLogger.printLine("<font color=gray><i>" + s + "</i></font>");
              } else {
                RequestLogger.printLine("<font color=gray>" + s + "</font>");
              }
            });
    client = new Builder().addInterceptor(loggingInterceptor).build();
  }

  private void updateLogLevel() {
    var loggingLevel = Preferences.getString("buffooneryHttpLoggingLevel");
    loggingInterceptor.setLevel(
        loggingLevel != null && !loggingLevel.isBlank() ? Level.valueOf(loggingLevel) : Level.NONE);
  }

  private HttpUrl.Builder buildUrl(String httpPath) {
    var baseUrl = Preferences.getString("buffooneryBaseUrl");
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("buffooneryBaseUrl not configured in preferences");
    }
    return HttpUrl.parse(baseUrl).newBuilder().addPathSegments(httpPath.replaceFirst("^/", ""));
  }

  public final BuffooneryResponse makeRequest(
      @NotNull String httpMethod,
      @NotNull String httpPath,
      @Nullable String query,
      @Nullable String json,
      long timeout)
      throws IOException {
    updateLogLevel();
    var token = Preferences.getString("buffooneryApiToken");
    var url = buildUrl(httpPath).encodedQuery(query).build();

    var client =
        timeout > 0
            ? this.client.newBuilder().readTimeout(timeout, TimeUnit.MILLISECONDS).build()
            : this.client;

    RequestBody body = (json == null || json.isBlank()) ? null : RequestBody.create(json, JSON);
    Request request =
        new Request.Builder()
            .url(url)
            .method(httpMethod, body)
            .header("Authorization", "Bearer " + token)
            .build();
    try (Response response = client.newCall(request).execute()) {
      var code = response.code();
      var responseBody = response.body();
      var stringBody = responseBody != null ? responseBody.string() : null;
      return new BuffooneryResponse(code, stringBody);
    }
  }

  public final Integer openWebSocket(@NotNull String httpPath, @NotNull String eventChannel) {
    var url = buildUrl(httpPath).build();
    Request request = new Request.Builder().url(url).build();

    var channel = MessageChannel.getChannel(eventChannel);
    final Integer handle = nextWebsocketHandle.getAndIncrement();

    WebSocketListener listener = new MessageChannelWebsocketListener(channel, handle);
    var websocket = this.client.newWebSocket(request, listener);
    openWebSockets.put(handle, websocket);
    return handle;
  }

  public final void closeWebSocket(Integer handle) {
    var websocket = openWebSockets.get(handle);
    if (websocket != null) {
      websocket.close(1000, "Websocket closed by user");
    }
  }

  private class MessageChannelWebsocketListener extends WebSocketListener {

    private final MessageChannel channel;
    private final Integer handle;

    public MessageChannelWebsocketListener(MessageChannel channel, Integer handle) {
      this.channel = channel;
      this.handle = handle;
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
      channel.postMessage(new Message("websocket_message", text));
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
      postMessage("websocket_open", new JSONObject());
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
      postMessage("websocket_closing", code, reason);
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
      postMessage("websocket_closed", code, reason);
      openWebSockets.remove(handle);
    }

    @Override
    public void onFailure(
        @NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
      var params = new JSONObject();
      params.put("message", t.getMessage());
      postMessage("websocket_failure", params);
      openWebSockets.remove(handle);
    }

    private void postMessage(String event, int code, @NotNull String reason) {
      var params = new JSONObject();
      params.put("code", code);
      params.put("reason", reason);
      postMessage(event, params);
    }

    private void postMessage(String event, JSONObject params) {
      params.put("handle", handle);
      channel.postMessage(new Message(event, params.toString()));
    }
  }
}
