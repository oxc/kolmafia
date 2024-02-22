package net.sourceforge.kolmafia.textui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import net.sourceforge.kolmafia.RequestLogger;
import org.jetbrains.annotations.Nullable;

public class MessageChannel {

  public static class Message {
    public final String event;

    public final @Nullable String params;

    public Message(String event, @Nullable String params) {
      this.event = event;
      this.params = params;
    }
  }

  private static final Map<String, MessageChannel> channels = new ConcurrentHashMap<>();

  public static MessageChannel getChannel(final String name) {
    return channels.computeIfAbsent(name, k -> new MessageChannel(name));
  }

  private final LinkedTransferQueue<Message> queue = new LinkedTransferQueue<>();

  private final String channelName;

  private MessageChannel(String channelName) {
    this.channelName = channelName;
  }

  public void postMessage(Message message) {
    queue.add(message);
  }

  public @Nullable Message pollMessage() {
    RequestLogger.printLine("pollMessage: " + channelName);
    return queue.poll();
  }

  public @Nullable Message pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
    RequestLogger.printLine("pollMessage: " + channelName + " " + timeout + " " + unit);
    return queue.poll(timeout, unit);
  }
}
