package java.util.concurrent;

/**
 * <tt>Conditions</tt> abstract out the <tt>Object</tt> monitor 
 * methods ({@link Object#wait() wait}, {@link Object#notify notify} and
 * {@link Object#notifyAll notifyAll}) into distinct objects that can be bound 
 * to other objects to give the effect of having multiple wait-sets per
 * object monitor. They also generalise the monitor methods to allow them to 
 * be used with arbitrary {@link Lock} implementations when needed.
 *
 * <p>Conditions (also known as condition queues or condition variables) 
 * provide
 * a means for one thread to suspend execution (to &quot;wait&quot;) until
 * notified by another thread that some state-condition the first thread is 
 * waiting for may now be true. Because access to this shared state information
 * occurs in different threads, it must be protected and invariably
 * a lock, of some form, is always associated with the condition. The key
 * property that waiting for a condition provides is that it 
 * <em>atomically</em> releases the associated lock and suspends the current
 * thread. 
 *
 * <p>A <tt>Condition</tt> instance is intrinsically bound to a lock: either
 * the in-built monitor lock of an object, or a {@link Lock} instance.
 * To obtain a <tt>Condition</tt> instance for a particular object's monitor 
 * lock
 * use the {@link Locks#newConditionFor(Object)} method.
 * To obtain a <tt>Condition</tt> instance for a particular {@link Lock} 
 * instance use its {@link Lock#newCondition} method.
 *
 * <p>As an example, suppose we have a bounded buffer which supports methods
 * to <tt>put</tt> and <tt>take</tt> items in/from the buffer. If a 
 * <tt>take</tt> is attempted on an empty buffer then the thread will block
 * until an item becomes available; if a <tt>put</tt> is attempted on a
 * full buffer, then the thread will block until a space becomes available.
 * We would like to keep waiting <tt>put</tt> threads and <tt>take</tt>
 * threads in separate wait-sets so that we can use the optimisation of
 * only notifying a single thread at a time when items, or spaces, become
 * available in the buffer. This can be achieved using either two 
 * {@link Condition} instances, or one {@link Condition} instance and the 
 * actual
 * monitor wait-set. For clarity we'll use two {@link Condition} instances.
 * <pre><code>
 * class BoundedBuffer {
 *   <b>final Condition notFull  = Locks.newConditionFor(this); 
 *   final Condition notEmpty = Locks.newConditionFor(this); </b>
 *
 *   Object[] items = new Object[100];
 *   int putptr, takeptr, count;
 *
 *   public <b>synchronized</b> void put(Object x) 
 *                              throws InterruptedException {
 *     while (count == items.length) 
 *       <b>notFull.await();</b>
 *     items[putptr] = x; 
 *     if (++putptr == items.length) putptr = 0;
 *     ++count;
 *     <b>notEmpty.signal();</b>
 *   }
 *
 *   public <b>synchronized</b> Object take() throws InterruptedException {
 *     while (count == 0) 
 *       <b>notEmpty.await();</b>
 *     Object x = items[takeptr]; 
 *     if (++takeptr == items.length) takeptr = 0;
 *     --count;
 *     <b>notFull.signal();</b>
 *     return x;
 *   } 
 * }
 * </code></pre>
 *
 * <p>If we were to use a standalone {@link Lock} object, such as a 
 * {@link ReentrantLock} then we would write the example as so:
 * <pre><code>
 * class BoundedBuffer {
 *   <b>Lock lock = new ReentrantLock();</b>
 *   final Condition notFull  = <b>lock.newCondition(); </b>
 *   final Condition notEmpty = <b>lock.newCondition(); </b>
 *
 *   Object[] items = new Object[100];
 *   int putptr, takeptr, count;
 *
 *   public void put(Object x) throws InterruptedException {
 *     <b>lock.lock();
 *     try {</b>
 *       while (count == items.length) 
 *         <b>notFull.await();</b>
 *       items[putptr] = x; 
 *       if (++putptr == items.length) putptr = 0;
 *       ++count;
 *       <b>notEmpty.signal();</b>
 *     <b>} finally {
 *       lock.unlock();
 *     }</b>
 *   }
 *
 *   public Object take() throws InterruptedException {
 *     <b>lock.lock();
 *     try {</b>
 *       while (count == 0) 
 *         <b>notEmpty.await();</b>
 *       Object x = items[takeptr]; 
 *       if (++takeptr == items.length) takeptr = 0;
 *       --count;
 *       <b>notFull.signal();</b>
 *       return x;
 *     <b>} finally {
 *       lock.unlock();
 *     }</b>
 *   } 
 * }
 * </code></pre>
 *
 * <p>A <tt>Condition</tt> implementation can provide behavior and semantics 
 * that is 
 * different from that of the <tt>Object</tt> monitor methods, such as 
 * guaranteed ordering for notifications, or not requiring a lock to be held 
 * when performing notifications.
 * If an implementation provides such specialised semantics then the 
 * implementation must document those semantics.
 *
 * <p>Note that <tt>Condition</tt> instances are just normal objects and can 
 * themselves be used as the target in a <tt>synchronized</tt> statement,
 * and can have their own monitor {@link Object#wait wait} and
 * {@link Object#notify notification} methods invoked.
 * Acquiring the monitor lock of a <tt>Condition</tt> instance, or using its
 * monitor methods has no specified relationship with acquiring the 
 * {@link Lock} associated with that <tt>Condition</tt> or the use of it's
 * {@link #await waiting} and {@link #signal signalling} methods.
 * It is recommended that to avoid confusion you never use <tt>Condition</tt>
 * instances in this way, except perhaps within their own implementation.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Implementation Considerations</h3>
 *
 * <p>When waiting upon a <tt>Condition</tt> instance a &quot;<em>spurious 
 * wakeup</em>&quot; is permitted to occur, in 
 * general, as a concession to the underlying platform semantics.
 * This has little practical impact on most application programs as a
 * <tt>Condition</tt> should always be waited upon in a loop, testing
 * the state predicate that is being waited for. An implementation is
 * free to remove the possibility of spurious wakeups but it is 
 * recommended that applications programmers always assume that they can
 * occur and so always wait in a loop.
 *
 * <p>It is recognised that the three forms of condition waiting 
 * (interruptible, non-interruptible, and timed) may differ in their ease of 
 * implementation on some platforms and in their performance characteristics.
 * In particular, it may be difficult to provide these features and maintain 
 * specific semantics such as ordering guarantees. 
 * Further, the ability to interrupt the actual suspension of the thread may 
 * not always be feasible to implement on all platforms.
 * <p>Consequently, an implementation is not required to define exactly the 
 * same guarantees or semantics for all three forms of waiting, nor is it 
 * required to support interruption of the actual suspension of the thread.
 * <p>An implementation is required to
 * clearly document the semantics and guarantees provided by each of the 
 * waiting methods, and when an implementation does support interruption of 
 * thread suspension then it must obey the interruption semantics as defined 
 * in this interface.
 *
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date$
 * @editor $Author$
 **/
public interface Condition {

    /**
     * Causes the current thread to wait until it is signalled or 
     * {@link Thread#interrupt interrupted}.
     *
     * <p>The lock associated with this <tt>Condition</tt> is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of four things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread, and interruption of thread suspension is supported; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this condition. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting 
     * and interruption of thread suspension is supported, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. It is not specified, in the first
     * case, whether or not the test for interruption occurs before the lock
     * is released.
     * 
     * <p>The circumstances under which the wait completes are mutually 
     * exclusive. For example, if the thread is signalled then it will
     * never return by throwing {@link InterruptedException}; conversely
     * a thread that is interrupted and throws {@link InterruptedException}
     * will never consume a {@link #signal}.
     *
     * <p><b>Implementation Considerations</b>
     * <p>The current thread is assumed to hold the lock associated with this
     * <tt>Condition</tt> when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation must document that fact.
     *
     * @throws InterruptedException if the current thread is interrupted (and
     * interruption of thread suspension is supported).
     **/
    public void await() throws InterruptedException;

    /**
     * Causes the current thread to wait until it is signalled.
     *
     * <p>The lock associated with this condition is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this condition. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     * <p>If the current thread's interrupt status is set when it enters
     * this method, or it is {@link Thread#interrupt interrupted} 
     * while waiting, it will continue to wait until signalled. When it finally
     * returns from this method it's <em>interrupted status</em> will still
     * be set.
     * 
     * <p><b>Implementation Considerations</b>
     * <p>The current thread is assumed to hold the lock associated with this
     * <tt>Condition</tt> when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation must document that fact.
     *
     **/
    public void awaitUninterruptibly();

    /**
     * Causes the current thread to wait until it is signalled or interrupted,
     * or the specified waiting time elapses.
     *
     * <p>The lock associated with this condition is atomically 
     * released and the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until <em>one</em> of five things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this 
     * <tt>Condition</tt> and the current thread happens to be chosen as the 
     * thread to be awakened; or 
     * <li>Some other thread invokes the {@link #signalAll} method for this 
     * <tt>Condition</tt>; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread, and interruption of thread suspension is supported; or
     * <li>The specified waiting time elapses; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
     * </ul>
     *
     * <p>In all cases, before this method can return the current thread must
     * re-acquire the lock associated with this condition. When the
     * thread returns it is <em>guaranteed</em> to hold this lock.
     *
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting 
     * and interruption of thread suspension is supported, 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. It is not specified, in the first
     * case, whether or not the test for interruption occurs before the lock
     * is released.
     *
     * <p>The circumstances under which the wait completes are mutually 
     * exclusive. For example, if the thread is signalled then it will
     * never return by throwing {@link InterruptedException}; conversely
     * a thread that is interrupted and throws {@link InterruptedException}
     * will never consume a {@link #signal}.
     *
     * <p>The method returns an estimate of the number of nanoseconds
     * remaining to wait given the supplied <tt>nanosTimeout</tt>
     * value upon return, or a value less than or equal to zero if it
     * timed out. This value can be used to determine whether and how
     * long to re-wait in cases where the wait returns but an awaited
     * condition still does not hold. Typical uses of this method take
     * the following form:
     *
     * <pre>
     * synchronized boolean aMethod(long timeout, TimeUnit unit) {
     *   long nanosTimeout = unit.toNanos(timeout);
     *   while (!conditionBeingWaitedFor) {
     *     if (nanosTimeout &gt; 0)
     *         nanosTimeout = theCondition.awaitNanos(nanosTimeout);
     *      else
     *        return false;
     *   }
     *   // ... 
     * }
     * </pre>
     *
     * <p> Design note: This method requires a nanosecond argument so
     * as to avoid truncation errors in reporting remaining times.
     * Such precision loss would make it difficult for programmers to
     * ensure that total waiting times are not systematically shorter
     * than specified when re-waits occur.
     *
     * <p><b>Implementation Considerations</b>
     * <p>The current thread is assumed to hold the lock associated with this
     * <tt>Condition</tt> when this method is called.
     * It is up to the implementation to determine if this is
     * the case and if not, how to respond. Typically, an exception will be 
     * thrown (such as {@link IllegalMonitorStateException}) and the
     * implementation must document that fact.
     *
     *
     * @param nanosTimeout the maximum time to wait, in nanoseconds
     * @return A value less than or equal to zero if the wait has
     * timed out; otherwise an estimate, that
     * is strictly less than the <tt>nanosTimeout</tt> argument,
     * of the time still remaining when this method returned.
     *
     * @throws InterruptedException if the current thread is interrupted (and
     * interruption of thread suspension is supported).
     */
    public long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * Wakes up one waiting thread.
     *
     * <p>If any threads are waiting on this condition then one
     * is selected for waking up. That thread must then re-acquire the
     * lock before returning from <tt>await</tt>.
     **/
    public void signal();

    /**
     * Wake up all waiting threads.
     *
     * <p>If any threads are waiting on this condition then they are
     * all woken up. Each thread must re-acquire the lock before it can
     * return from <tt>await</tt>.
     **/
    public void signalAll();

}




