package java.util.concurrent;

/**
 * A <tt>Lock</tt> provides more flexible locking operations than
 * can be obtained using <tt>synchronized</tt> methods and statements.
 *
 * <p>A <tt>Lock</tt> is a tool for controlling access to a shared
 * resource by multiple threads. Commonly, a lock provides exclusive access
 * to a shared resource: only one thread at a time can acquire the
 * lock and all access to the shared resource requires that the lock be
 * acquired first. However, some locks may allow concurrent access to a shared
 * resource, such as the read lock of a {@link ReadWriteLock}.
 *
 * <p>The use of <tt>synchronized</tt> methods or statements provides 
 * access to the implicit monitor lock associated with every object, but
 * forces all lock acquisition and release to occur in a block-structured way:
 * when multiple locks are acquired they must be released in the opposite
 * order, and all locks must be released in the same lexical scope in which
 * they were acquired.
 *
 * <p>While the scoping mechanism for <tt>synchronized</tt> methods and 
 * statements make it much easier to program with monitor locks,
 * and helps avoid many common programming errors involving locks, there are
 * rare occasions where you need to work with locks in a more flexible way. For
 * example, some advanced algorithms for traversing concurrently accessed data
 * structures require the use of what is called &quot;hand-over-hand&quot; or 
 * &quot;chain locking&quote: you acquire the lock of node A, then node B, 
 * then release A and acquire C, then release B and acquire D and so on. 
 * Implementations of the <tt>Lock</tt> class facilitate the use of such 
 * advanced algorithms by allowing a lock to be acquired and released in 
 * different scopes, and allowing multiple locks to be acquired and released 
 * in any order. 
 *
 * <p>With this increased flexibilty comes
 * additional responsibility as the absence of block-structured locking
 * removes the automatic release of locks that occurs with 
 * <tt>synchronized</tt> methods and statements. For the simplest usage
 * the following idiom should be used:
 * <pre><tt>     Lock l = ...; 
 *     l.lock();
 *     try {
 *         // access the resource protected by this lock
 *     }
 *     finally {
 *         l.unlock();
 *     }
 * </tt></pre>
 *
 * <p>A <tt>Lock</tt> also provides additional functionality over the use
 * of <tt>synchronized</tt> methods and statements by providing a non-blocking
 * attempt to acquire a lock ({@link #tryLock()}), an attempt to acquire the
 * lock that can be interrupted ({@link #lockInterruptibly}, and an attempt
 * to acquire the lock that can timeout ({@link #tryLock(long, Clock)}).
 * This additionally functionality is also extended to built-in monitor
 * locks throughthe methods of the {@link Locks} utility class.
 *
 * <p>A <tt>Lock</tt> can also provide behaviour and semantics that is quite
 * different to that of the implicit monitor lock, such as guaranteed ordering,
 * non-reentrant usage, or deadlock detection. If an implementation provides
 * such specialised semantics then the implementation must document those
 * semantics.
 *
 * <p>All <tt>Lock</tt> implementations <em>must</em> enforce the same
 * memory synchronization semantics as provided by the built-in monitor lock:
 * <ul>
 * <li>A successful lock operation  acts like a successful 
 * <tt>monitorEnter</tt> action
 * <li>A successful <tt>unlock</tt> operation acts like a successful
 * <tt>monitorExit</tt> action
 * </ul>
 * Note that unsuccessful locking and unlocking operations, and reentrant
 * locking/unlocking operations, do not require any memory synchronization
 * effects.
 *
 * <p>It is recognised that the three forms of lock acquisition (interruptible,
 * non-interruptible, and timed) may differ in their ease of implementation
 * on some platforms and in their performance characteristics.
 * In particular, it may be difficult to provide these features and maintain 
 * specific semantics such as ordering guarantees. Consequently an 
 * implementation is not required to define exactly the same guarantees or
 * semantics for all three forms of lock acquistion; but it is required to
 * clearly document the semantics and guarantees provided by each of them.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * @see ReentrantLock
 * @see Condition
 * @see ReadWriteLock
 * @see Locks
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date$
 * @editor $Author$
 *
 * @fixme We need to say something about l.lock() versus synchronized(l)
 **/
public interface Lock {

    /**
     * Acquire the lock. 
     * <p>Acquires the lock if it is available and returns immediately.
     * <p>If the lock is not available then
     * the current thread thread becomes disabled for thread scheduling 
     * purposes and lies dormant until the lock has been acquired.
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     **/
    public void lock();

    /**
     * Acquire the lock only if the current thread is not 
     * {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is available and returns immediately.
     * <p>If the lock is not available then
     * the current thread thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of two things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the current thread is {@link Thread#interrupt interrupted} 
     * while waiting to acquire the lock then {@link InterruptedException}
     * is thrown and the current thread's <em>interrupted status</em> 
     * is cleared.
     *
     * <p>The ability to interrupt a lock acquisition in some implementations
     * could reasonably be foreseen to be an expensive operation. 
     * The programmer should be aware that this may be the case. An
     * implementation should document when this is the case.
     *
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock.
     *
     * @see Thread#interrupt
     *
     * @fixme This allows for interruption only if waiting actually occurs.
     * Is this correct? Is it too strict?
     **/
    public void lockInterruptibly() throws InterruptedException;


    /**
     * Acquire the lock only if it is free at the time of invocation.
     * <p>Acquires the lock if it is available and returns immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then this method will return 
     * immediately with the value <tt>false</tt>.
     * <p>A typical usage idiom for this method would be:
     * <pre>
     *      Lock lock = ...;
     *      if (lock.tryLock()) {
     *          try {
     *              // manipulate protected state
     *          finally {
     *              lock.unlock();
     *          }
     *      }
     *      else {
     *          // perform alternative actions
     *      }
     * </pre>
     * This usage ensures that the lock is unlocked if it was acquired, and
     * doesn't try to unlock if the lock was not acquired.
     *
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * otherwise.
     **/
    public boolean tryLock();

    /**
     * Acquire the lock if it is free within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}. 
     * <p>Acquires the lock if it is available and returns immediately
     * with the value <tt>true</tt>.
     * <p>If the lock is not available then
     * the current thread thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li> The specified waiting time elapses
     * </ul>
     * <p>If the lock is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread is {@link Thread#interrupt interrupted} 
     * while waiting to acquire the lock then {@link InterruptedException}
     * is thrown and the current thread's <em>interrupted status</em> 
     * is cleared.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * <p>The given waiting time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all, but may 
     * still throw an <tt>InterruptedException</tt> if the thread is 
     * {@link Thread#interrupt interrupted}.
     *
     * <p>A concrete <tt>Lock</tt> implementation may be able to detect 
     * erroneous use of the
     * lock, such as an invocation that would cause deadlock, and may throw 
     * an exception in such circumstances. The circumstances and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     *
     * @param time the maximum time to wait for the lock
     * @param granularity the time unit of the <tt>time</tt> argument.
     * @return <tt>true</tt> if the lock was acquired and <tt>false</tt>
     * if the waiting time elapsed before the lock was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * while trying to acquire the lock.
     *
     * @see Thread#interrupt
     *
     * @fixme We have inconsistent interrupt semantics if the thread is
     * interrupted before calling this method: if the lock is available
     * we won't throw IE, if the lock is not available and the timeout is <=0
     * then we may throw IE. Need to resolve this.
     *
     **/
    public boolean tryLock(long time, Clock granularity) throws InterruptedException;

    /**
     * Release the lock.
     * <p>A concrete <tt>Lock</tt> implementation will usually impose
     * restrictions on which thread can release a lock (typically only the
     * holder of the lock can release it) and may throw
     * an exception if the restriction is violated. 
     * Any restrictions and the exception
     * type must be documented by the <tt>Lock</tt> implementation.
     **/
    public void unlock();

    /**
     * Return a {@link Condition} that is bound to this <tt>Lock</tt>.
     * Conditions are primarily used with the built-in locking provided by
     * <tt>synchronized</tt> methods and statements 
     * (see {@link Locks#newConditionFor}, but in some rare circumstances it 
     * can be useful to wait for a condition when working with a data 
     * structure that is accessed using a stand-alone <tt>Lock</tt> class 
     * (see {@link ReentrantLock}). Before waiting on the condition the 
     * <tt>Lock</tt> must be acquired by the caller. 
     * A call to {@link Condition#await()} will atomically release the lock 
     * before waiting and re-acquire the lock before the wait returns.
     * <p>The exact operation of the {@link Condition} depends on the concrete
     * <tt>Lock</tt> implementation and must be documented by that
     * implementation.
     * 
     * @return A {@link Condition} object for this <tt>Lock</tt>, or
     * <tt>null</tt> if this <tt>Lock</tt> type does not support conditions.
     *
     **/
    public Condition newCondition();

}









