/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * An object that executes submitted tasks. This interface provides a
 * way of decoupling task submission from the mechanics of how each
 * task will be run, including details of thread use, scheduling, etc.
 *
 * <p>In the simplest case, an executor can run the submitted task immediately
 * in the caller's thread:
 *
 * <pre>
 * class DirectExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         r.run();
 *     }
 * }</pre>
 *
 * However, tasks are typically executed in a different thread than
 * the caller's thread.  The executor below spawns a new thread for
 * each task.
 *
 * <pre>
 * class ThreadPerTaskExecutor implements Executor {
 *     public void execute(Runnable r) {
 *         new Thread(r).start();
 *     }
 * }</pre>
 *
 * Most <tt>Executor</tt> implementations will impose some sort of limitation
 * on how and when tasks are scheduled.  The executor below serializes the
 * submission of tasks to a second executor, illustrating a composite executor.
 *
 * <pre>
 * class SerialExecutor implements Executor {
 *     LinkedQueue tasks = new LinkedQueue<Runnable>();
 *     Executor executor;
 *     Runnable active;
 *
 *     SerialExecutor(Executor executor) {
 *         this.executor = executor;
 *     }
 *
 *     public synchronized void execute(final Runnable r) {
 *         tasks.offer(new Runnable() {
 *             public void run() {
 *                 try {
 *                     r.run();
 *                 } finally {
 *                     scheduleNext();
 *                 }
 *             }
 *         });
 *         if (active == null) {
 *             scheduleNext();
 *         }
 *     }
 *
 *     protected synchronized void scheduleNext() {
 *         if ((active = tasks.poll()) != null) {
 *             executor.execute(active);
 *         }
 *     }
 * }</pre>
 *
 * The <tt>Executor</tt> implementations provided in
 * <tt>java.util.concurrent</tt> implement <tt>ExecutorService</tt>,
 * which is a more extensive interface.  For more advanced users, the
 * <tt>ThreadPoolExecutor</tt> class provides a powerful, extensible
 * thread pool implementation. The <tt>Executors</tt> class provides
 * convenient factory methods for these executors.
 *
 * @since 1.5
 * @see Executors
 * @see FutureTask
 *
 * @spec JSR-166
 * @revised $Date$
 * @editor $Author$
 * @author Doug Lea
 */
public interface Executor {

    /**
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the <tt>Executor</tt> implementation.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if task cannot be submitted for
     * execution
     */
    void execute(Runnable command);
}
