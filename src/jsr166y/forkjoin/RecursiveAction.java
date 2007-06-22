/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Recursive resultless ForkJoinTasks. To maintain conformance with
 * other classes in this framework, RecursiveActions are parameterized
 * as <tt>Void</tt> ForkJoinTasks, and return <tt>null</tt> as
 * results. But for simplicity and efficiency, the <tt>compute</tt>
 * method and related methods use <tt>void</tt>.  RecursiveActions
 * normally proceed via parallel divide and conquer; very often using
 * the convenient (and typically more efficient) combined method
 * <tt>coInvoke</tt>.  Here is a sketch of a ForkJoin sort that sorts
 * a given <tt>long[]</tt> array:
 *
 *
 * <pre>
 * class SortTask extends RecursiveAction {
 *   final long[] array; final int lo; final int hi;
 *   SortTask(long[] array, int lo, int hi) {
 *     this.array = array; this.lo = lo; this.hi = hi;
 *   }
 *   protected void compute() {
 *     if (hi - lo &lt; THRESHOLD)
 *       sequentiallySort(array, lo, hi);
 *     else {
 *       int mid = (lo + hi) &gt;&gt;&gt; 1;
 *       coInvoke(new SortTask(array, lo, mid),
 *                new SortTask(array, mid+1, hi));
 *       merge(array, lo, hi);
 *     }
 *     return null;
 *   }
 * }
 * </pre>
 *
 * You could then sort anArray by creating <tt>new SortTask(anArray, 0,
 * anArray.length-1) </tt> and invoking it in a ForkJoinPool. 
 *
 * <p>RecursiveActions need not be fully recursive, so long as they
 * maintain the basic divide-and-conquer approach. For example, here
 * is a class that sums the squares of each element of a double array,
 * by subdividing out only the right-hand-sides of repeated divisions
 * by two, and keeping track of them with a chain of <tt>next</tt>
 * references. It uses a common rule of thumb for granularity
 * settings, corresponding to about four times as many base tasks as
 * there are threads in the pool.
 *
 * <pre>
 * double sumOfSquares(ForkJoinPool pool, double[] array) {
 *   int n = array.length;
 *   int seqSize = 1 + n / (4 * pool.getPoolSize() - 3);
 *   Applier a = new Applier(array, 0, n-1, seqSize, null);
 *   pool.invoke(a);
 *   return a.result;
 * }
 *
 * class Applier extends RecursiveAction {
 *   final double[] array;
 *   final int lo, hi, seqSize;
 *   int result;
 *   Applier next; // keeps track of right-hand-side tasks
 *   Applier(double[] array, int lo, int hi, int seqSize, Applier next) {
 *     this.array = array; this.lo = lo; this.hi = hi;
 *     this.seqSize = seqSize; this.next = next;
 *   }
 *
 *   protected void compute() {
 *     int l = lo;
 *     int h = hi;
 *     Applier right = null;
 *     while (h - l &gt; seqSize) { // fork right-hand sides
 *        int mid = (l + h) &gt;&gt;&gt 1;
 *        right = new Applier(array, mid+1, h, seqSize, right);
 *        right.fork();
 *        h = mid;
 *     }
 *     double sum = 0;
 *     for (int i = l; i &lt;= h; ++i) // perform leftmost base step
 *       sum += array[i] * array[i];
 *     while (right != null) {  // join right-hand sides
 *       right.join();
 *       sum += right.result;
 *       right = right.next;
 *     }
 *     result = sum;
 *   }
 * }
 * </pre>
 */
public abstract class RecursiveAction extends ForkJoinTask<Void> {
    /**
     * The main computation performed by this task.  While you must
     * define this method, you should not in general call it directly.
     * To immediately perform the computation, use <tt>invoke</tt>.
     */
    protected abstract void compute();

    /**
     * Forks both tasks and returns when <tt>isDone</tt> holds for
     * both, cancelling one if the other encounters an exception. If
     * both tasks encounter exceptions, only one of them (arbitrarily
     * chosen) is thrown from this method.  You can check individual
     * status using method <tt>getException</tt>, noting that if a
     * cancelled task had not completed before the other failed, it
     * may return a <tt>CancellationException</tt>.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @throws RuntimeException if thrown in either task's compute methods
     */
    public static void coInvoke(RecursiveAction t1, RecursiveAction t2) {
        ((ForkJoinPool.Worker)(Thread.currentThread())).coInvokeTasks(t1, t2);
    }

    /**
     * Forks all tasks in the array, returning when <tt>isDone</tt>
     * holds for all of them. If any task encounters an exception,
     * others are cancelled.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @throws RuntimeException if thrown in any task's compute method
     */
    public static void coInvoke(RecursiveAction[] tasks) {
        ((ForkJoinPool.Worker)(Thread.currentThread())).coInvokeTasks(tasks);
    }

    /**
     * Forks all tasks in the list, returning when <tt>isDone</tt>
     * holds for all of them. If any task encounters an exception,
     * others are cancelled.
     * This method may be invoked only from within other ForkJoinTask
     * computations. Attempts to invoke in other contexts result
     * in exceptions or errors including ClassCastException.
     * @throws RuntimeException if thrown in any task's compute method
     */
    public static void coInvoke(List<? extends RecursiveAction> tasks) {
        ((ForkJoinPool.Worker)(Thread.currentThread())).coInvokeTasks(tasks);
    }

    /**
     * Always returns null.
     * @return null
     */
    public final Void getResult() { 
        return null; 
    }

    public final Void invoke() {
        try {
            if (exception == null)
                compute();
        } catch(RuntimeException rex) {
            exceptionUpdater.compareAndSet(this, null, rex);
            throw rex;
        }
        RuntimeException ex = setDone();
        if (ex != null)
            throw ex;
        return null;
    }

    public final RuntimeException exec() {
        if (exception == null) {
            try {
                compute();
            } catch(RuntimeException rex) {
                exceptionUpdater.compareAndSet(this, null, rex);
            }
        }
        return setDone();
    }

    /**
     * Equivalent to <tt>finish(null)</tt>.
     */
    public final void finish() { 
        setDone();
    }

    public final void finish(Void result) { 
        setDone();
    }

    public final void finishExceptionally(RuntimeException ex) {
        casException(ex);
    }

}
