package net.sourceforge.kolmafia.oxc;

import java.io.IOException;
import java.net.URL;
import net.sourceforge.kolmafia.preferences.Preferences;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuffooneryHttpClient {

  public static final BuffooneryHttpClient INSTANCE = new BuffooneryHttpClient();

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient client;

  private BuffooneryHttpClient() {
    client = new OkHttpClient();
  }

  public final String makeRequest(
      @NotNull String httpMethod,
      @NotNull String httpPath,
      @Nullable String query,
      @Nullable String json
  ) throws IOException {
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
      return response.body().string();
    }
  }
}
