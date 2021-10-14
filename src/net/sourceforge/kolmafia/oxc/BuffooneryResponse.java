package net.sourceforge.kolmafia.oxc;

import org.jetbrains.annotations.Nullable;

public class BuffooneryResponse {

  private final int code;
  @Nullable private final String body;

  public BuffooneryResponse(int code, @Nullable String body) {
    this.code = code;
    this.body = body;
  }

  public int getCode() {
    return code;
  }

  @Nullable
  public String getBody() {
    return body;
  }
}
