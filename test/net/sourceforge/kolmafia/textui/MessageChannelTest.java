package net.sourceforge.kolmafia.textui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import net.sourceforge.kolmafia.textui.MessageChannel.Message;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MessageChannelTest {

  private static MessageChannel channelWith(final String... events) {
    // channels are global and never removed, so give every test its own
    var channel = MessageChannel.getChannel("test-" + System.nanoTime());
    for (var event : events) {
      channel.postMessage(new Message(event, null));
    }
    return channel;
  }

  private static List<String> drain(final MessageChannel channel) {
    var events = new ArrayList<String>();
    for (Message message = channel.pollMessage();
        message != null;
        message = channel.pollMessage()) {
      events.add(message.event);
    }
    return events;
  }

  @Nested
  class PollByEvent {
    @Test
    void takesTheMessageWithTheGivenEvent() {
      var channel = channelWith("first", "wanted", "last");

      var message = channel.pollMessage("wanted");

      assertThat(message, is(notNullValue()));
      assertThat(message.event, is("wanted"));
    }

    @Test
    void leavesTheOtherMessagesInOrder() {
      var channel = channelWith("first", "wanted", "second", "third");

      channel.pollMessage("wanted");

      assertThat(drain(channel), contains("first", "second", "third"));
    }

    @Test
    void takesOnlyTheFirstMatch() {
      var channel = channelWith("wanted", "other", "wanted");

      channel.pollMessage("wanted");

      assertThat(drain(channel), contains("other", "wanted"));
    }

    @Test
    void returnsNothingAndTakesNothingWhenNoMessageMatches() {
      var channel = channelWith("first", "second");

      assertThat(channel.pollMessage("wanted"), is(nullValue()));
      assertThat(drain(channel), contains("first", "second"));
    }

    @Test
    void returnsNothingOnAnEmptyChannel() {
      var channel = channelWith();

      assertThat(channel.pollMessage("wanted"), is(nullValue()));
    }
  }
}
