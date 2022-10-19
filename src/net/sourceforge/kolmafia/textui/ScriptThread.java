package net.sourceforge.kolmafia.textui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sourceforge.kolmafia.KoLmafiaASH;
import net.sourceforge.kolmafia.KoLmafiaCLI;
import net.sourceforge.kolmafia.textui.parsetree.Value;

public class ScriptThread extends Thread {

  private static final Map<String, ScriptThread> threads = new ConcurrentHashMap<>();

  private final String threadName;

  private final ScriptRuntime runtime;
  private final String functionName;
  private final Object[] scriptParameters;
  private Value returnValue;

  private ScriptThread(
      String threadName,
      ScriptRuntime runtime,
      final String functionName,
      final Object[] scriptParameters) {
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
      final Object[] scriptParameters) {
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

  public static void interruptAll() {
    for (ScriptThread thread : threads.values()) {
      thread.interrupt();
    }
  }

  @Override
  public void run() {
    this.returnValue = null;
    runtime.setState(ScriptRuntime.State.NORMAL);
    this.returnValue = runtime.execute(functionName, scriptParameters);
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
