package loop;

import loop.ast.script.Unit;
import org.junit.Before;
import org.junit.Test;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Measures the performance of various Loop scripts compiled against the MVEL and
 * ASM/Java Bytecode backends.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AsmMvelPerformanceTest {
  private static final int RUNS = 500000;

  @Before
  public final void before() {
    LoopClassLoader.reset();
  }

  @Test
  public final void loopFunctionCalling() throws Exception {
    Callable callable = new Callable() {

      @Override public Method lookup(Class target) throws Exception {
        return target.getDeclaredMethod("func");
      }

      @Override public Object call(Method target) throws Exception {
        return target.invoke(null);
      }
    };

    time("cons(x) ->\n  '1'\n\nfunc() ->\n  cons('10')", callable, "func();");
  }

  @Test
  public final void loopFunctionCallingWithIntegrals() throws Exception {
    Callable callable = new Callable() {

      @Override public Method lookup(Class target) throws Exception {
        return target.getDeclaredMethod("func");
      }

      @Override public Object call(Method target) throws Exception {
        return target.invoke(null);
      }
    };

    time("cons(x) ->\n  1 + 2\n\nfunc() ->\n  cons(10)", callable, "func();");
  }

  @Test
  public final void integerAddition() throws Exception {
    Callable add = new Callable() {

      @Override public Method lookup(Class target) throws Exception {
        return target.getDeclaredMethod("add", Object.class, Object.class, Object.class);
      }

      @Override public Object call(Method target) throws Exception {
        return target.invoke(null, 10, 20, 40);
      }
    };

    time("add(x, y, z) ->\n  x + y + z\n", add, "add(10, 20, 40);");
  }

  @Test
  public final void arithmetic() throws Exception {
    Callable add = new Callable() {

      @Override public Method lookup(Class target) throws Exception {
        return target.getDeclaredMethod("arith", Object.class, Object.class);
      }

      @Override public Object call(Method target) throws Exception {
        return target.invoke(null, 10, 20);
      }
    };

    time("arith(x, y) ->\n  ((100 + x - y) * 10 / 2) % 40\n", add, "arith(10, 20);");
  }

  @Test
  public final void collectionAddition() throws Exception {
    Callable add = new Callable() {

      @Override public Method lookup(Class target) throws Exception {
        return target.getDeclaredMethod("add", Object.class, Object.class);
      }

      @Override public Object call(Method target) throws Exception {
        return target.invoke(null, Arrays.asList(10, 20), Arrays.asList(40));
      }
    };

    time("add(x, y) ->\n  x + y\n", add, "add([10, 20], [40]);");
  }

  public static void time(String script, Callable javaCallable, String mvelCallable) throws Exception {
    System.out.println("Profiling loop script:");
    System.out.println(script);
    System.out.println("\n\n");
    Parser parser = new Parser(new Tokenizer(script).tokenize());
    Unit unit = parser.script();
    unit.reduceAll();

    // Compile ASM.
    Class<?> generated = new AsmCodeEmitter(unit).write(unit);
    Method asmCallable = javaCallable.lookup(generated);

    // Compile MVEL.
    String mvel = new MvelCodeEmitter(unit).write(unit);
    mvel += "; " + mvelCallable;
    Serializable compiledMvel = MVEL.compileExpression(mvel);

    // Assert validity.
    Object javaGen = javaCallable.call(asmCallable);
    Object mvelGen = MVEL.executeExpression(compiledMvel, new HashMap());
    assertNotNull(javaGen);
    assertNotNull(mvelGen);

    // MVEL incorrectly type-widens certain integer arithmetic expressions to double. If so,
    // convert it back into an integer.
    if (mvelGen instanceof Double && !(javaGen instanceof Double))
      mvelGen = ((Double)mvelGen).intValue();

    assertEquals(javaGen, mvelGen);

    // Warm up JVM.
    for (int i = 0; i < 15000; i++) {
       javaCallable.call(asmCallable);
       MVEL.executeExpression(compiledMvel, new HashMap());
    }

    long start = System.currentTimeMillis();
    for (int i = 0; i < RUNS; i++) {
      MVEL.executeExpression(compiledMvel, new HashMap());
    }
    System.out.println("Mvel runtime: " + (System.currentTimeMillis() - start));

    start = System.currentTimeMillis();
    for (int i = 0; i < RUNS; i++) {
      javaCallable.call(asmCallable);
    }
    System.out.println("Asm runtime: " + (System.currentTimeMillis() - start));
    System.out.println();
  }

  public static interface Callable {
    Method lookup(Class target) throws Exception;
    Object call(Method target) throws Exception;
  }
}