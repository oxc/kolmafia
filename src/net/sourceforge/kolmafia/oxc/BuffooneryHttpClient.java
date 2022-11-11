package net.sourceforge.kolmafia.oxc;

import com.alibaba.fastjson2.JSONObject;
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

  private long getPref(String key, long def) {
    var value = Preferences.getLong(key);
    return value != 0 ? value : def;
  }

  @NotNull
  public final OkHttpClient.Builder buildClient() {
    return client
        .newBuilder()
        .connectTimeout(getPref("buffooneryHttpConnectTimeout", 5000), TimeUnit.MILLISECONDS)
        .readTimeout(getPref("buffooneryHttpReadTimeout", 5000), TimeUnit.MILLISECONDS)
        .writeTimeout(getPref("buffooneryHttpWriteTimeout", 5000), TimeUnit.MILLISECONDS);
  }

  @NotNull
  private HttpUrl.Builder buildUrl(String httpPath) {
    var baseUrl = Preferences.getString("buffooneryBaseUrl");
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("buffooneryBaseUrl not configured in preferences");
    }
    HttpUrl url = HttpUrl.parse(baseUrl);
    if (url == null) {
      throw new IllegalStateException("Invalid buffooneryBaseUrl configured: " + baseUrl);
    }
    return url.newBuilder().addPathSegments(httpPath.replaceFirst("^/", ""));
  }

  @NotNull
  private Request buildRequest(
      @NotNull String httpMethod,
      @NotNull String httpPath,
      @Nullable String query,
      @Nullable String json) {
    var token = Preferences.getString("buffooneryApiToken");
    HttpUrl.Builder urlBuilder = buildUrl(httpPath);
    if (query != null && !query.isBlank()) {
      urlBuilder.encodedQuery(query);
    }
    var url = urlBuilder.build();

    RequestBody body = (json == null || json.isBlank()) ? null : RequestBody.create(json, JSON);
    return new Request.Builder()
        .url(url)
        .method(httpMethod, body)
        .header("Authorization", "Bearer " + token)
        .build();
  }

  public final BuffooneryResponse makeRequest(
      @NotNull String httpMethod,
      @NotNull String httpPath,
      @Nullable String query,
      @Nullable String json,
      long timeout)
      throws IOException {
    updateLogLevel();

    var clientBuilder = buildClient();
    if (timeout > 0) {
      clientBuilder.callTimeout(timeout, TimeUnit.MILLISECONDS);
    }
    var client = clientBuilder.build();

    Request request = buildRequest(httpMethod, httpPath, query, json);
    try (Response response = client.newCall(request).execute()) {
      var code = response.code();
      var responseBody = response.body();
      var stringBody = responseBody != null ? responseBody.string() : null;
      return new BuffooneryResponse(code, stringBody);
    }
  }

  public final Integer openWebSocket(
      @NotNull String httpPath,
      @Nullable String query,
      long pingInterval,
      @NotNull String eventChannel) {
    var request = buildRequest("GET", httpPath, query, null);

    var channel = MessageChannel.getChannel(eventChannel);
    final Integer handle = nextWebsocketHandle.getAndIncrement();

    var clientBuilder = buildClient();
    clientBuilder.callTimeout(
        getPref("buffooneryHttpWebsocketCallTimeout", 10_000), TimeUnit.MILLISECONDS);
    if (pingInterval > 0) {
      clientBuilder.pingInterval(pingInterval, TimeUnit.MILLISECONDS);
    }
    var client = clientBuilder.build();

    WebSocketListener listener = new MessageChannelWebsocketListener(channel, handle);
    var websocket = client.newWebSocket(request, listener);
    openWebSockets.put(handle, websocket);
    return handle;
  }

  public final boolean sendWebSocketMessage(@NotNull Integer handle, @NotNull String message) {
    var websocket = openWebSockets.get(handle);
    if (websocket != null) {
      websocket.send(message);
      return true;
    }
    return false;
  }

  public final void closeWebSocket(Integer handle, int code, String reason) {
    var websocket = openWebSockets.get(handle);
    if (websocket != null) {
      websocket.close(code, reason);
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
