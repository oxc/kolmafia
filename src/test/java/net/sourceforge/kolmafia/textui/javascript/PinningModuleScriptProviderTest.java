package net.sourceforge.kolmafia.textui.javascript;

import static internal.helpers.Player.withProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import internal.helpers.Cleanups;
import org.junit.jupiter.api.Test;

class PinningModuleScriptProviderTest {
  @Test
  void pinsNothingByDefault() {
    var cleanups = withProperty("jsPinnedScripts", "");

    try (cleanups) {
      assertThat(PinningModuleScriptProvider.getPinnedScripts(), is(empty()));
      assertThat(PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery.js"), is(false));
    }
  }

  @Test
  void readsScriptNamesFromThePreference() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery,buffoonery_chatHandler");

    try (cleanups) {
      assertThat(
          PinningModuleScriptProvider.getPinnedScripts(),
          containsInAnyOrder("buffoonery", "buffoonery_chatHandler"));
    }
  }

  @Test
  void ignoresSpacingAndEmptyNames() {
    var cleanups = withProperty("jsPinnedScripts", " buffoonery , ,, ");

    try (cleanups) {
      assertThat(PinningModuleScriptProvider.getPinnedScripts(), contains("buffoonery"));
    }
  }

  @Test
  void followsThePreferenceWhenItChanges() {
    var cleanups = new Cleanups(withProperty("jsPinnedScripts", "buffoonery"));

    try (cleanups) {
      assertThat(PinningModuleScriptProvider.getPinnedScripts(), contains("buffoonery"));

      try (var changed = withProperty("jsPinnedScripts", "buffoonery_chatPoller")) {
        assertThat(
            PinningModuleScriptProvider.getPinnedScripts(), contains("buffoonery_chatPoller"));
      }
    }
  }

  @Test
  void pinsAScriptByItsNameWhereverItIsInstalled() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery_chatHandler");

    try (cleanups) {
      assertThat(
          PinningModuleScriptProvider.isPinned(
              "file:/buffoonery/kolmafia-data/scripts/buffoonery_chatHandler.js"),
          is(true));
      assertThat(
          PinningModuleScriptProvider.isPinned(
              "file:/home/someone/KoLmafia/scripts/buffoonery_chatHandler.js"),
          is(true));
    }
  }

  @Test
  void doesNotPinScriptsTheNameDoesNotCover() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery_chatHandler");

    try (cleanups) {
      assertThat(
          PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery_chatPoller.js"),
          is(false));
      // a name that merely contains the pinned one is a different script
      assertThat(
          PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery_chatHandler_old.js"),
          is(false));
    }
  }

  @Test
  void aPinnedNameCoversTheDirectoryOfThatName() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery");

    try (cleanups) {
      // the bundle's entry point, and the chunks it shares with the other entry points
      assertThat(PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery.js"), is(true));
      assertThat(
          PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery/loop-DEg2OsSo.js"),
          is(true));
      assertThat(
          PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery/nested/deeper.js"),
          is(true));
    }
  }

  @Test
  void doesNotPinByTheInstallPathAroundTheScriptsDirectory() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery");

    try (cleanups) {
      // the bots live in /buffoonery, which says nothing about which scripts they want kept
      assertThat(
          PinningModuleScriptProvider.isPinned(
              "file:/buffoonery/kolmafia-data/scripts/somebody_elses.js"),
          is(false));
    }
  }

  @Test
  void pinsAScriptWithoutAnExtension() {
    var cleanups = withProperty("jsPinnedScripts", "buffoonery");

    try (cleanups) {
      assertThat(PinningModuleScriptProvider.isPinned("file:/scripts/buffoonery"), is(true));
    }
  }
}
