package net.sourceforge.kolmafia.textui.javascript;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.KoLmafiaASH;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.textui.AshRuntime;
import net.sourceforge.kolmafia.textui.ScriptException;
import net.sourceforge.kolmafia.textui.parsetree.Function;
import net.sourceforge.kolmafia.textui.parsetree.UserDefinedFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.provider.ParsedContentType;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;

public class SafeRequire extends Require {
  private static final long serialVersionUID = 1L;

  /**
   * Shared across every script invocation, so that a module's compiled form is reused instead of
   * being recompiled from source each time a script runs.
   *
   * <p>A Require is bound to the scope it was created for, so we cannot share the Require itself --
   * but the module script provider is what holds the compiled modules, and it is what needs to
   * outlive a single execution. Rhino compiles with a class-generating optimization level, so every
   * recompile defines a fresh set of classes under a new DefiningClassLoader; a bot re-entering a
   * script per chat message recompiled its whole module graph every time and ratcheted Metaspace's
   * committed high-water mark up, which is never handed back to the OS.
   *
   * <p>Sharing is safe: CachingModuleScriptProviderBase keeps its cache in a ConcurrentMap behind
   * striped load locks, and it revalidates each cached module against the source provider, so
   * editing a script on disk still recompiles it. Modules are held softly, and so still released
   * under memory pressure, unless jsPinnedScripts names them -- see PinningModuleScriptProvider.
   */
  private static final PinningModuleScriptProvider MODULE_SCRIPT_PROVIDER =
      new PinningModuleScriptProvider(new KoLmafiaUrlModuleSourceProvider());

  private final Scriptable stdLib;

  public SafeRequire(Context cx, Scriptable nativeScope, Scriptable stdLib) {
    super(cx, nativeScope, MODULE_SCRIPT_PROVIDER, null, new MainWarningScript(), true);
    this.stdLib = stdLib;
  }

  @Override
  public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args == null || args.length < 1 || !(args[0] instanceof String path)) {
      throw new ScriptException("require() needs one argument, a string");
    }

    if (path.equals("kolmafia")) {
      return stdLib;
    } else if (path.endsWith(".ash")) {
      Scriptable exports = cx.newObject(scope);

      List<File> scriptFiles = KoLmafiaCLI.findScriptFile(path);
      List<File> validScriptFiles =
          scriptFiles.stream()
              .filter(
                  f -> {
                    try {
                      return f.getCanonicalPath()
                          .startsWith(KoLConstants.ROOT_LOCATION.getCanonicalPath());
                    } catch (IOException e) {
                      KoLmafia.updateDisplay(
                          MafiaState.ERROR, "Could not resolve path " + f.getPath());
                      return false;
                    }
                  })
              .collect(Collectors.toList());
      AshRuntime interpreter = (AshRuntime) KoLmafiaASH.getInterpreter(validScriptFiles);

      if (interpreter == null) {
        throw new ScriptException("Module \"" + path + "\" not found.");
      }

      for (Function f : interpreter.getFunctions()) {
        UserDefinedFunction userDefinedFunction = (UserDefinedFunction) f;
        String functionName = userDefinedFunction.getName();
        String functionNameCamelCase = JavascriptRuntime.toCamelCase(functionName);
        if (!ScriptableObject.hasProperty(exports, functionNameCamelCase)) {
          UserDefinedFunctionStub stub =
              new UserDefinedFunctionStub(
                  exports,
                  ScriptableObject.getFunctionPrototype(exports),
                  interpreter,
                  functionName);
          int attributes =
              ScriptableObject.DONTENUM | ScriptableObject.PERMANENT | ScriptableObject.READONLY;
          ScriptableObject.defineProperty(exports, functionNameCamelCase, stub, attributes);
        }
      }

      interpreter.execute(null, null);

      return exports;
    } else {
      // Require itself checks sandboxing.
      return super.call(cx, scope, thisObj, args);
    }
  }

  private static class KoLmafiaUrlModuleSourceProvider extends UrlModuleSourceProvider {
    public KoLmafiaUrlModuleSourceProvider() {
      super(
          Arrays.asList(KoLConstants.ROOT_LOCATION.toURI(), KoLConstants.SCRIPT_LOCATION.toURI()),
          null);
    }

    // modify to not treat text/javascript files as latin-1, but always utf-8 if unknown
    @Override
    protected String getCharacterEncoding(URLConnection urlConnection) {
      final ParsedContentType pct = new ParsedContentType(urlConnection.getContentType());
      final String encoding = pct.getEncoding();
      if (encoding != null) {
        return encoding;
      }
      return "utf-8";
    }
  }
}
