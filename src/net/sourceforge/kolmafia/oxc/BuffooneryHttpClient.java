package net.sourceforge.kolmafia.oxc;

import java.io.IOException;
import java.net.URL;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.preferences.Preferences;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import okhttp3.logging.HttpLoggingInterceptor.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuffooneryHttpClient {

  public static final BuffooneryHttpClient INSTANCE = new BuffooneryHttpClient();

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient client;

  private final HttpLoggingInterceptor loggingInterceptor;

  private BuffooneryHttpClient() {
    loggingInterceptor = new HttpLoggingInterceptor(RequestLogger::printLine);
    client = new Builder().addInterceptor(loggingInterceptor).build();
  }

  public final BuffooneryResponse makeRequest(
      @NotNull String httpMethod,
      @NotNull String httpPath,
      @Nullable String query,
      @Nullable String json
  ) throws IOException {
    var loggingLevel = Preferences.getString("buffooneryHttpLoggingLevel");
    loggingInterceptor.setLevel(loggingLevel != null ? Level.valueOf(loggingLevel) : Level.NONE);
    var baseUrl = Preferences.getString("buffooneryBaseUrl");
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("buffooneryBaseUrl not configured in preferences");
    }
    var url = HttpUrl.parse(baseUrl)
        .newBuilder()
        .addPathSegments(httpPath)
        .encodedQuery(query)
        .build();

    RequestBody body = (json == null || json.isBlank()) ? null : RequestBody.create(json, JSON);
    Request request = new Request.Builder()
        .url(url)
        .method(httpMethod, body)
        .build();
    try (Response response = client.newCall(request).execute()) {
      var code = response.code();
      var responseBody = response.body();
      var stringBody = responseBody != null ? responseBody.string() : null;
      return new BuffooneryResponse(code, stringBody);
    }
  }
}
