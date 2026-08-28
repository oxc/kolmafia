package net.sourceforge.kolmafia.textui.javascript;

import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.sourceforge.kolmafia.preferences.Preferences;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.StrongCachingModuleScriptProvider;

/**
 * Caches compiled modules, sending the ones named by the {@code jsPinnedScripts} preference to a
 * cache that keeps them and everything else to one that lets them go when memory is short. Both
 * caches are Rhino's own, and they differ in nothing but that.
 *
 * <p>Rhino compiles with a class-generating optimization level, so a module that has fallen out of
 * the cache is not merely re-read but re-compiled, into a fresh set of classes under a fresh
 * DefiningClassLoader. Unloading those hands their Metaspace back to the JVM but never to the OS,
 * whose committed high-water mark is what a container's memory limit is measured against. A script
 * that runs over and over -- a chat handler, a poller, anything re-entered on an event -- therefore
 * wants to stay compiled, while soft references decide that by how much heap happens to be free and
 * how long it has been since the last message, which is not a property of the script at all.
 *
 * <p>The preference is a comma separated list of script names relative to the scripts directory and
 * without their extension, so {@code buffoonery_chatHandler} pins {@code
 * scripts/buffoonery_chatHandler.js}. A name that is a directory pins everything under it: {@code
 * buffoonery} covers {@code scripts/buffoonery.js} and every module beside it in {@code
 * scripts/buffoonery/}, which is where a bundler puts the chunks its entry points share -- those
 * are most of what gets compiled, and their names carry a content hash, so there is nothing stable
 * to name them by one at a time.
 *
 * <p>This changes only how long a compiled module is kept, never whether it is still the right one:
 * both caches revalidate what they hold against its source, so editing a script on disk recompiles
 * it either way. Naming a script that is already loaded takes effect on its next compile rather
 * than moving it between the caches, which costs that one compile and nothing after it.
 */
public class PinningModuleScriptProvider implements ModuleScriptProvider, Serializable {
  private static final long serialVersionUID = 1L;

  private static final String SCRIPTS_DIRECTORY = "/scripts/";

  private final ModuleScriptProvider pinnedModules;
  private final ModuleScriptProvider otherModules;

  public PinningModuleScriptProvider(final ModuleSourceProvider moduleSourceProvider) {
    this.pinnedModules = new StrongCachingModuleScriptProvider(moduleSourceProvider);
    this.otherModules = new SoftCachingModuleScriptProvider(moduleSourceProvider);
  }

  @Override
  public ModuleScript getModuleScript(
      final Context cx,
      final String moduleId,
      final URI uri,
      final URI base,
      final Scriptable paths)
      throws Exception {
    // Asked per lookup rather than remembered per module, so that naming a script takes effect on
    // the next thing that loads it rather than on the next restart.
    ModuleScriptProvider provider = isPinned(moduleId) ? this.pinnedModules : this.otherModules;
    return provider.getModuleScript(cx, moduleId, uri, base, paths);
  }

  private static String pinnedScriptsPreference;
  private static Set<String> pinnedScripts = Set.of();

  /**
   * The script names the preference asks us to hold on to, parsed once per value it takes: this is
   * consulted for every module of every script run, and the preference changes almost never.
   */
  static synchronized Set<String> getPinnedScripts() {
    String preference = Preferences.getString("jsPinnedScripts");
    if (!preference.equals(pinnedScriptsPreference)) {
      pinnedScriptsPreference = preference;
      pinnedScripts =
          Arrays.stream(preference.split(","))
              .map(String::trim)
              .filter(name -> !name.isEmpty())
              .collect(Collectors.toUnmodifiableSet());
    }
    return pinnedScripts;
  }

  /** Whether this module is one to hold on to, by its name or by the directory it sits in. */
  static boolean isPinned(final String moduleId) {
    Set<String> pinnedScripts = getPinnedScripts();
    if (pinnedScripts.isEmpty()) {
      return false;
    }

    String name = scriptName(moduleId);
    if (pinnedScripts.contains(name)) {
      return true;
    }
    for (int slash = name.indexOf('/'); slash > 0; slash = name.indexOf('/', slash + 1)) {
      if (pinnedScripts.contains(name.substring(0, slash))) {
        return true;
      }
    }
    return false;
  }

  /**
   * What to call a module in the preference: its path below the scripts directory, without the
   * extension. Module ids are URIs of the file that was loaded, and a script is named by where it
   * sits in scripts/, not by the absolute path a particular install gives it -- which for the
   * scripts we most want to pin is a path that may well repeat their own name.
   */
  private static String scriptName(final String moduleId) {
    String path = moduleId;
    int scripts = path.lastIndexOf(SCRIPTS_DIRECTORY);
    if (scripts >= 0) {
      path = path.substring(scripts + SCRIPTS_DIRECTORY.length());
    } else {
      path = path.substring(path.lastIndexOf('/') + 1);
    }

    int extension = path.lastIndexOf('.');
    return extension < 0 ? path : path.substring(0, extension);
  }
}
