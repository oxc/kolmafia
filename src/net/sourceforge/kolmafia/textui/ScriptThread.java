package net.sourceforge.kolmafia.textui;

import static net.java.dev.spellcast.utilities.DataUtilities.convertToHTML;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.KoLmafiaASH;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.textui.parsetree.Value;
import org.jetbrains.annotations.Nullable;

public class ScriptThread extends Thread {

  private static final Map<String, ScriptThread> threads = new ConcurrentHashMap<>();

  private final String threadName;

  private final ScriptRuntime runtime;
  private final String functionName;
  private final @Nullable Value[] scriptParameters;
  private Value returnValue;

  private boolean keepRunning = true;

  private ScriptThread(
      String threadName,
      ScriptRuntime runtime,
      final String functionName,
      final @Nullable Value[] scriptParameters) {
    super("Script thread: " + threadName);
    this.threadName = threadName;
    this.runtime = runtime;
    this.functionName = functionName;
    this.scriptParameters = scriptParameters;
  }

  public static ScriptThread getOrCreateScriptThread(
      String threadName,
      String scriptName,
      final String functionName,
      final Value[] scriptParameters) {
    return threads.compute(
        threadName,
        (k, existingThread) -> {
          if (existingThread != null && existingThread.isAlive()) {
            return existingThread;
          }
          var scriptFiles = KoLmafiaCLI.findScriptFile(scriptName);
          var interpreter = KoLmafiaASH.getInterpreter(scriptFiles);
          if (interpreter == null) {
            return null;
          }
          return new ScriptThread(threadName, interpreter, functionName, scriptParameters);
        });
  }

  public static @Nullable ScriptThread getScriptThread(String threadName) {
    return threads.get(threadName);
  }

  public static @Nullable ScriptThread currentScriptThread() {
    var currentThread = Thread.currentThread();
    if (currentThread instanceof ScriptThread) {
      return (ScriptThread) currentThread;
    }
    return null;
  }

  public static void stopAll() {
    for (ScriptThread thread : threads.values()) {
      thread.requestStop();
      thread.interrupt();
    }
  }

  @Override
  public void run() {
    this.returnValue = null;
    try {
      runtime.setState(ScriptRuntime.State.NORMAL);
      var returnValue = runtime.execute(functionName, scriptParameters);

      this.returnValue = returnValue;
      if (returnValue == null) {
        String escapedMessage =
            "Script thread <tt>"
                + convertToHTML(threadName)
                + "</tt> return null. Either an exception was thrown, or the function does not exist?";

        RequestLogger.printLine(MafiaState.ERROR, escapedMessage);
      }
    } catch (Exception e) {
      String escapedMessage =
          "Exception in script thread <tt>"
              + convertToHTML(threadName)
              + "</tt>: "
              + convertToHTML(e.getMessage());

      RequestLogger.printLine(MafiaState.ERROR, escapedMessage);
      throw e;
    }
  }

  public void requestStop() {
    this.keepRunning = false;
  }

  public boolean shouldContinue() {
    return this.keepRunning;
  }

  public boolean isRunning() {
    return this.isAlive();
  }

  public void startIfNotRunning() {
    if (!this.isRunning()) {
      this.start();
    }
  }

  public Value getReturnValue() {
    return returnValue;
  }
}
