/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * A {@link ThreadPoolExecutor} that can schedule commands to run
 * after a given delay, or to execute periodically. This class is
 * preferable to {@link java.util.Timer} when multiple worker threads
 * are needed, or when the additional flexibility or capabilities of
 * {@link ThreadPoolExecutor} (which this class extends) are required.
 *
 * <p> Delayed tasks execute no sooner than they are enabled, but
 * without any real-time guarantees about when, after they are enabled,
 * they will commence. Tasks tied for the same execution time are
 * enabled in first-in-first-out (FIFO) order of submission. 
 *
 * <p>While this class inherits from {@link ThreadPoolExecutor}, a few
 * of the inherited tuning methods are not especially useful for
 * it. In particular, because a <tt>ScheduledExecutor</tt> always acts
 * as a fixed-sized pool using <tt>corePoolSize</tt> threads and an
 * unbounded queue, adjustments to <tt>maximumPoolSize</tt> have no
 * useful effect.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ScheduledThreadPoolExecutor 
        extends ThreadPoolExecutor 
        implements ScheduledExecutorService {

    /**
     * False if should cancel/suppress periodic tasks on shutdown.
     */
    private volatile boolean continueExistingPeriodicTasksAfterShutdown;

    /**
     * False if should cancel non-periodic tasks on shutdown.
     */
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;


    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong(0);
    
    private static class ScheduledFutureTask<V> 
            extends FutureTask<V> implements ScheduledFuture<V> {
        
        /** Sequence number to break ties FIFO */
        private final long sequenceNumber;
        /** The time the task is enabled to execute in nanoTime units */
        private long time;
        /** The delay following next time, or <= 0 if non-periodic */
        private final long period;
        /** true if at fixed rate; false if fixed delay */
        private final boolean rateBased; 


        /**
         * Creates a one-shot action with given nanoTime-based trigger time
         */
        ScheduledFutureTask(Runnable r, V result, long ns) {
            super(r, result);
            this.time = ns;
            this.period = 0;
            rateBased = false;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a periodic action with given nano time and period
         */
        ScheduledFutureTask(Runnable r, V result, long ns,  long period, boolean rateBased) {
            super(r, result);
            this.time = ns;
            this.period = period;
            this.rateBased = rateBased;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a one-shot action with given nanoTime-based trigger
         */
        ScheduledFutureTask(Callable<V> callable, long ns) {
            super(callable);
            this.time = ns;
            this.period = 0;
            rateBased = false;
            this.sequenceNumber = sequencer.getAndIncrement();
        }


        public long getDelay(TimeUnit unit) {
            long d =  unit.convert(time - System.nanoTime(), 
                                   TimeUnit.NANOSECONDS);
            return d;
        }

        public int compareTo(Object other) {
            if (other == this) // compare zero ONLY if same object
                return 0;
            ScheduledFutureTask<?> x = (ScheduledFutureTask<?>)other;
            long diff = time - x.time;
            if (diff < 0)
                return -1;
            else if (diff > 0)
                return 1;
            else if (sequenceNumber < x.sequenceNumber)
                return -1;
            else
                return 1;
        }

        /**
         * Return true if this is a periodic (not a one-shot) action.
         * @return true if periodic
         */
        public boolean isPeriodic() {
            return period > 0;
        }

        /**
         * Returns the period, or zero if non-periodic.
         *
         * @return the period
         */
        public long getPeriod(TimeUnit unit) {
            return unit.convert(period, TimeUnit.NANOSECONDS);
        }

        /**
         * Overrides FutureTask version so as to reset if periodic.
         */ 
        public void run() {
            if (isPeriodic())
                runAndReset();
            else
                super.run();
        }

        /**
         * Return a task (which may be this task) that will trigger in
         * the period subsequent to current task, or null if
         * non-periodic or cancelled.
         */
        ScheduledFutureTask nextTask() {
            if (period <= 0 || !reset())
                return null;
            time = period + (rateBased ? time : System.nanoTime());
            return this;
        }
    }

    /**
     * An annoying wrapper class to convince generics compiler to
     * use a DelayQueue<ScheduledFutureTask> as a BlockingQueue<Runnable>
     */ 
    private static class DelayedWorkQueue 
            extends AbstractCollection<Runnable> implements BlockingQueue<Runnable> {
        
        private final DelayQueue<ScheduledFutureTask> dq = new DelayQueue<ScheduledFutureTask>();
        public Runnable poll() { return dq.poll(); }
        public Runnable peek() { return dq.peek(); }
        public Runnable take() throws InterruptedException { return dq.take(); }
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return dq.poll(timeout, unit);
        }

        public boolean add(Runnable x) { return dq.add((ScheduledFutureTask)x); }
        public boolean offer(Runnable x) { return dq.offer((ScheduledFutureTask)x); }
        public void put(Runnable x)  {
            dq.put((ScheduledFutureTask)x); 
        }
        public boolean offer(Runnable x, long timeout, TimeUnit unit) {
            return dq.offer((ScheduledFutureTask)x, timeout, unit);
        }

        public Runnable remove() { return dq.remove(); }
        public Runnable element() { return dq.element(); }
        public void clear() { dq.clear(); }
        public int drainTo(Collection<? super Runnable> c) { return dq.drainTo(c); }
        public int drainTo(Collection<? super Runnable> c, int maxElements) { 
            return dq.drainTo(c, maxElements); 
        }

        public int remainingCapacity() { return dq.remainingCapacity(); }
        public boolean remove(Object x) { return dq.remove(x); }
        public boolean contains(Object x) { return dq.contains(x); }
        public int size() { return dq.size(); }
        public boolean isEmpty() { return dq.isEmpty(); }
        public Object[] toArray() { return dq.toArray(); }
        public <T> T[] toArray(T[] array) { return dq.toArray(array); }
        public Iterator<Runnable> iterator() { 
            return new Iterator<Runnable>() {
                private Iterator<ScheduledFutureTask> it = dq.iterator();
                public boolean hasNext() { return it.hasNext(); }
                public Runnable next() { return it.next(); }
                public void remove() {  it.remove(); }
            };
        }
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given core pool size.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @throws IllegalArgumentException if corePoolSize less than or
     * equal to zero
     */
    public ScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue());
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param threadFactory the factory to use when the executor
     * creates a new thread. 
     * @throws NullPointerException if threadFactory is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                             ThreadFactory threadFactory) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), threadFactory);
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     * @throws NullPointerException if handler is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), handler);
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given initial parameters.
     * 
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param threadFactory the factory to use when the executor
     * creates a new thread. 
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached.
     * @throws NullPointerException if threadFactory or handler is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.NANOSECONDS,
              new DelayedWorkQueue(), threadFactory, handler);
    }

    /**
     * Specialized variant of ThreadPoolExecutor.execute for delayed tasks.
     */
    private void delayedExecute(Runnable command) {
        if (isShutdown()) {
            reject(command);
            return;
        }
        // Prestart a thread if necessary. We cannot prestart it
        // running the task because the task (probably) shouldn't be
        // run yet, so thread will just idle until delay elapses.
        if (getPoolSize() < getCorePoolSize())
            prestartCoreThread();
            
        super.getQueue().add(command);
    }

    /**
     * Creates and executes a one-shot action that becomes enabled after
     * the given delay.
     * @param command the task to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a Future representing pending completion of the task,
     * and whose <tt>get()</tt> method will return <tt>Boolean.TRUE</tt>
     * upon completion.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     * @throws NullPointerException if command is null
     */

    public ScheduledFuture<Boolean> schedule(Runnable command, long delay,  TimeUnit unit) {
        if (command == null)
            throw new NullPointerException();
        long triggerTime = System.nanoTime() + unit.toNanos(delay);
        ScheduledFutureTask<Boolean> t = new ScheduledFutureTask<Boolean>(command, Boolean.TRUE, triggerTime);
        delayedExecute(t);
        return t;
    }

    /**
     * Creates and executes a ScheduledFuture that becomes enabled after the
     * given delay.
     * @param callable the function to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @return a ScheduledFuture that can be used to extract result or cancel.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     * @throws NullPointerException if callable is null
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (callable == null)
            throw new NullPointerException();
        long triggerTime = System.nanoTime() + unit.toNanos(delay);
        ScheduledFutureTask<V> t = new ScheduledFutureTask<V>(callable, triggerTime);
        delayedExecute(t);
        return t;
    }

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period; that is executions will commence after
     * <tt>initialDelay</tt> then <tt>initialDelay+period</tt>, then
     * <tt>initialDelay + 2 * period</tt>, and so on.  The
     * task will only terminate via cancellation.
     * @param command the task to execute.
     * @param initialDelay the time to delay first execution.
     * @param period the period between successive executions.
     * @param unit the time unit of the delay and period parameters
     * @return a Future representing pending completion of the task,
     * and whose <tt>get()</tt> method will throw an exception upon
     * cancellation.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if period less than or equal to zero.
     */
    public ScheduledFuture<Boolean> scheduleAtFixedRate(Runnable command, long initialDelay,  long period, TimeUnit unit) {
        if (command == null)
            throw new NullPointerException();
        if (period <= 0)
            throw new IllegalArgumentException();
        long triggerTime = System.nanoTime() + unit.toNanos(initialDelay);
        ScheduledFutureTask<Boolean> t = new ScheduledFutureTask<Boolean>
            (command, Boolean.TRUE,
             triggerTime,
             unit.toNanos(period), 
             true);
        delayedExecute(t);
        return t;
    }
    
    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next. 
     * The task will only terminate via cancellation.
     * @param command the task to execute.
     * @param initialDelay the time to delay first execution.
     * @param delay the delay between the termination of one
     * execution and the commencement of the next.
     * @param unit the time unit of the delay and delay parameters
     * @return a Future representing pending completion of the task,
     * and whose <tt>get()</tt> method will throw an exception upon
     * cancellation.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution because the executor has been shut down.
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if delay less than or equal to zero.
     */
    public ScheduledFuture<Boolean> scheduleWithFixedDelay(Runnable command, long initialDelay,  long delay, TimeUnit unit) {
        if (command == null)
            throw new NullPointerException();
        if (delay <= 0)
            throw new IllegalArgumentException();
        long triggerTime = System.nanoTime() + unit.toNanos(initialDelay);
        ScheduledFutureTask<Boolean> t = new ScheduledFutureTask<Boolean>
            (command, 
             Boolean.TRUE,
             triggerTime,
             unit.toNanos(delay), 
             false);
        delayedExecute(t);
        return t;
    }
    

    /**
     * Execute command with zero required delay. This has effect
     * equivalent to <tt>schedule(command, 0, anyUnit)</tt>.  Note
     * that inspections of the queue and of the list returned by
     * <tt>shutdownNow</tt> will access the zero-delayed
     * {@link ScheduledFuture}, not the <tt>command</tt> itself.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     * <tt>RejectedExecutionHandler</tt>, if task cannot be accepted
     * for execution because the executor has been shut down.
     * @throws NullPointerException if command is null
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }


    /**
     * Set policy on whether to continue executing existing periodic
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * false.
     * @param value if true, continue after shutdown, else don't.
     */
    public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
        continueExistingPeriodicTasksAfterShutdown = value;
        if (!value && isShutdown())
            cancelUnwantedTasks();
    }

    /**
     * Get the policy on whether to continue executing existing
     * periodic tasks even when this executor has been
     * <tt>shutdown</tt>. In this case, these tasks will only
     * terminate upon <tt>shutdownNow</tt> or after setting the policy
     * to <tt>false</tt> when already shutdown. This value is by
     * default false.
     * @return true if will continue after shutdown.
     */
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return continueExistingPeriodicTasksAfterShutdown;
    }

    /**
     * Set policy on whether to execute existing delayed
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * true.
     * @param value if true, execute after shutdown, else don't.
     */
    public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
        executeExistingDelayedTasksAfterShutdown = value;
        if (!value && isShutdown())
            cancelUnwantedTasks();
    }

    /**
     * Get policy on whether to execute existing delayed
     * tasks even when this executor has been <tt>shutdown</tt>. In
     * this case, these tasks will only terminate upon
     * <tt>shutdownNow</tt>, or after setting the policy to
     * <tt>false</tt> when already shutdown. This value is by default
     * true.
     * @return true if will execute after shutdown.
     */
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return executeExistingDelayedTasksAfterShutdown;
    }

    /**
     * Cancel and clear the queue of all tasks that should not be run
     * due to shutdown policy.
     */
    private void cancelUnwantedTasks() {
        boolean keepDelayed = getExecuteExistingDelayedTasksAfterShutdownPolicy();
        boolean keepPeriodic = getContinueExistingPeriodicTasksAfterShutdownPolicy();
        if (!keepDelayed && !keepPeriodic) 
            super.getQueue().clear();
        else if (keepDelayed || keepPeriodic) {
            Object[] entries = super.getQueue().toArray();
            for (int i = 0; i < entries.length; ++i) {
                ScheduledFutureTask<?> t = (ScheduledFutureTask<?>)entries[i];
                if (t.isPeriodic()? !keepPeriodic : !keepDelayed)
                    t.cancel(false);
            }
            entries = null;
            purge();
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted. If the
     * <tt>ExecuteExistingDelayedTasksAfterShutdownPolicy</tt> has
     * been set <tt>false</tt>, existing delayed tasks whose delays
     * have not yet elapsed are cancelled. And unless the
     * <tt>ContinueExistingPeriodicTasksAfterShutdownPolicy</tt> has
     * been set <tt>true</tt>, future executions of existing periodic
     * tasks will be cancelled.
     */
    public void shutdown() {
        cancelUnwantedTasks();
        super.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks that were
     * awaiting execution. 
     *  
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementations
     * cancels via {@link Thread#interrupt}, so if any tasks mask or
     * fail to respond to interrupts, they may never terminate.
     *
     * @return list of tasks that never commenced execution.  Each
     * element of this list is a {@link ScheduledFuture},
     * including those tasks submitted using <tt>execute</tt> which
     * are for scheduling purposes used as the basis of a zero-delay
     * <tt>ScheduledFuture</tt>.
     */
    public List shutdownNow() {
        return super.shutdownNow();
    }
            
    /**
     * Removes this task from internal queue if it is present, thus
     * causing it not to be run if it has not already started.  This
     * method may be useful as one part of a cancellation scheme.
     *
     * @param task the task to remove
     * @return true if the task was removed
     */
    public boolean remove(Runnable task) {
        if (task instanceof ScheduledFuture) 
            return super.remove(task);

        // The task might actually have been wrapped as a ScheduledFuture
        // in execute(), in which case we need to manually traverse
        // looking for it.

        ScheduledFuture wrap = null;
        Object[] entries = super.getQueue().toArray();
        for (int i = 0; i < entries.length; ++i) {
            ScheduledFutureTask<?> t = (ScheduledFutureTask<?>)entries[i];
            Object r = t.getTask();
            if (task.equals(r)) {
                wrap = t;
                break;
            }
        }
        entries = null;
        return wrap != null && super.getQueue().remove(wrap);
    }


    /**
     * Returns the task queue used by this executor.  Each element of
     * this queue is a {@link ScheduledFuture}, including those
     * tasks submitted using <tt>execute</tt> which are for scheduling
     * purposes used as the basis of a zero-delay
     * <tt>ScheduledFuture</tt>. Iteration over this queue is
     * </em>not</em> guaranteed to traverse tasks in the order in
     * which they will execute.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return super.getQueue();
    }

    /**
     * Override of <tt>Executor</tt> hook method to support periodic
     * tasks.  If the executed task was periodic, causes the task for
     * the next period to execute.
     * @param r the task (assumed to be a ScheduledFuture)
     * @param t the exception
     */
    protected void afterExecute(Runnable r, Throwable t) { 
        super.afterExecute(r, t);
        ScheduledFutureTask<?> next = ((ScheduledFutureTask<?>)r).nextTask();
        if (next != null &&
            (!isShutdown() ||
             (getContinueExistingPeriodicTasksAfterShutdownPolicy() && 
              !isTerminating())))
            super.getQueue().add(next);

        // This might have been the final executed delayed task.  Wake
        // up threads to check.
        else if (isShutdown()) 
            interruptIdleWorkers();
    }
}