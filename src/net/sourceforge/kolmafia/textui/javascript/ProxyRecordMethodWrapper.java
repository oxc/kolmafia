package net.sourceforge.kolmafia.textui.javascript;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.textui.ScriptException;
import net.sourceforge.kolmafia.textui.parsetree.ProxyRecordValue;
import net.sourceforge.kolmafia.textui.parsetree.Value;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

public class ProxyRecordMethodWrapper extends BaseFunction {
  private static final long serialVersionUID = 1L;

  private final Method method;

  public ProxyRecordMethodWrapper(Scriptable scope, Scriptable prototype, Method method) {
    super(scope, prototype);
    this.method = method;
  }

  @Override
  public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (!(thisObj instanceof EnumeratedWrapper)) {
      return null;
    }

    try {
      Object returnValue = method.invoke(((EnumeratedWrapper) thisObj).getWrapped().asProxy());
      RequestLogger.printLine("return value: " + returnValue);

      if (returnValue instanceof Value
          && ((Value) returnValue).asProxy() instanceof ProxyRecordValue) {
        RequestLogger.printLine("return value is a proxy record");
        returnValue = EnumeratedWrapper.wrap(scope, returnValue.getClass(), (Value) returnValue);
      } else if (!(returnValue instanceof Scriptable)) {
        RequestLogger.printLine("return value is not a scriptable");
        returnValue = Context.javaToJS(returnValue, scope);
      }

      if (returnValue instanceof NativeJavaObject) {
        throw new ScriptException("ASH function " + method + " returned native Java object.");
      }

      return returnValue;
    } catch (IllegalAccessException e) {
      return null;
    } catch (InvocationTargetException e) {
      return null;
    }
  }
}
