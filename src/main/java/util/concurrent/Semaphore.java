/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

/**
 * A counting semaphore.  Conceptually, a semaphore maintains a set of
 * permits.  Each {@link #acquire} blocks if necessary until a permit is
 * available, and then takes it.  Each {@link #release} adds a permit,
 * potentially releasing a blocking acquirer.
 * However, no actual permit objects are used; the <tt>Semaphore</tt> just
 * keeps a count of the number available and acts accordingly.
 *
 * <p>Semaphores are often used to restrict the number of threads than can
 * access some (physical or logical) resource. For example, here is
 * a class that uses a semaphore to control access to a pool of items:
 * <pre>
 * class Pool {
 *   private static final MAX_AVAILABLE = 100;
 *   private final Semaphore available = new Semaphore(MAX_AVAILABLE, true);
 *
 *   public Object getItem() throws InterruptedException {
 *     available.acquire();
 *     return getNextAvailableItem();
 *   }
 *
 *   public void putItem(Object x) {
 *     if (markAsUnused(x))
 *       available.release();
 *   }
 *
 *   // Not a particularly efficient data structure; just for demo
 *
 *   protected Object[] items = ... whatever kinds of items being managed
 *   protected boolean[] used = new boolean[MAX_AVAILABLE];
 *
 *   protected synchronized Object getNextAvailableItem() {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (!used[i]) {
 *          used[i] = true;
 *          return items[i];
 *       }
 *     }
 *     return null; // not reached
 *   }
 *
 *   protected synchronized boolean markAsUnused(Object item) {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (item == items[i]) {
 *          if (used[i]) {
 *            used[i] = false;
 *            return true;
 *          } else
 *            return false;
 *       }
 *     }
 *     return false;
 *   }
 *
 * }
 * </pre>
 *
 * <p>Before obtaining an item each thread must acquire a permit from
 * the semaphore, guaranteeing that an item is available for use. When
 * the thread has finished with the item it is returned back to the
 * pool and a permit is returned to the semaphore, allowing another
 * thread to acquire that item.  Note that no synchronization lock is
 * held when {@link #acquire} is called as that would prevent an item
 * from being returned to the pool.  The semaphore encapsulates the
 * synchronization needed to restrict access to the pool, separately
 * from any synchronization needed to maintain the consistency of the
 * pool itself.
 *
 * <p>A semaphore initialized to one, and which is used such that it
 * only has at most one permit available, can serve as a mutual
 * exclusion lock.  This is more commonly known as a <em>binary
 * semaphore</em>, because it only has two states: one permit
 * available, or zero permits available.  When used in this way, the
 * binary semaphore has the property (unlike many {@link Lock}
 * implementations), that the &quot;lock&quot; can be released by a
 * thread other than the owner (as semaphores have no notion of
 * ownership).  This can be useful in some specialized contexts, such
 * as deadlock recovery.
 *
 * <p> The constructor for this class accepts a <em>fairness</em>
 * parameter. When set false, this class makes no guarantees about the
 * order in which threads acquire permits. In particular, <em>barging</em> is
 * permitted, that is, a thread invoking {@link #acquire} can be
 * allocated a permit ahead of a thread that has been waiting.  When
 * fairness is set true, the semaphore guarantees that threads
 * invoking any of the {@link #acquire() acquire} methods are
 * allocated permits in the order in which their invocation of those
 * methods was processed (first-in-first-out; FIFO). Note that FIFO
 * ordering necessarily applies to specific internal points of
 * execution within these methods.  So, it is possible for one thread
 * to invoke <tt>acquire</tt> before another, but reach the ordering
 * point after the other, and similarly upon return from the method.
 *
 * <p>Generally, semaphores used to control resource access should be
 * initialized as fair, to ensure that no thread is starved out from
 * accessing a resource. When using semaphores for other kinds of
 * synchronization control, the throughput advantages of non-fair
 * ordering often outweigh fairness considerations.
 *
 * <p>This class also provides convenience methods to {@link
 * #acquire(int) acquire} and {@link #release(int) release} multiple
 * permits at a time.  Beware of the increased risk of indefinite
 * postponement when these methods are used without fairness set true.
 *
 * @since 1.5
 * @author Doug Lea
 *
 */

public class Semaphore implements java.io.Serializable {
    private static final long serialVersionUID = -3222578661600680210L;
    /** Sync mechanics via AbstractQueuedSynchronizer subclass */
    private final Sync sync;


    private final static class Sync extends AbstractQueuedSynchronizer {
        final boolean fair;
        Sync(int permits, boolean fair) {
            this.fair = fair;
            getState().set(permits);
        }
        
        public final int acquireSharedState(boolean isQueued, int acquires, 
                                            Thread current) {
            final AtomicInteger perms = getState();
            if (!isQueued && fair && hasWaiters())
                return -1;
            for (;;) {
                int available = perms.get();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    perms.compareAndSet(available, remaining))
                    return remaining;
            }
        }
        
        public final boolean releaseSharedState(int releases) {
            final AtomicInteger perms = getState();
            for (;;) {
                int p = perms.get();
                if (perms.compareAndSet(p, p + releases)) 
                    return true;
            }
        }
        
        public final int acquireExclusiveState(boolean isQueued, 
                                               int acquires, 
                                               Thread current) {
            throw new UnsupportedOperationException();
        }
        
        public final boolean releaseExclusiveState(int releases) {
            throw new UnsupportedOperationException();
        }
        
        public final void checkConditionAccess(Thread thread, boolean waiting) {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * Construct a <tt>Semaphore</tt> with the given number of
     * permits and the given fairness setting.
     * @param permits the initial number of permits available. This
     * value may be negative, in which case releases must
     * occur before any acquires will be granted.
     * @param fair true if this semaphore will guarantee first-in
     * first-out granting of permits under contention, else false.
     */
    public Semaphore(int permits, boolean fair) { 
        sync = new Sync(permits, fair);
    }

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available, or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     */
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit.
     *
     * <p>If the current thread
     * is {@link Thread#interrupt interrupted} while waiting
     * for a permit then it will continue to wait, but the time at which
     * the thread is assigned a permit may change compared to the time it
     * would have received the permit had no interruption occurred. When the
     * thread does return from this method its interrupt status will be set.
     *
     */
    public void acquireUninterruptibly() {
        sync.acquireSharedUninterruptibly(1);
    }

    /**
     * Acquires a permit from this semaphore, only if one is available at the 
     * time of invocation.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value <tt>false</tt>.
     *
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * otherwise.
     */
    public boolean tryAcquire() {
        return sync.acquireSharedState(false, 1, null) >= 0;
    }

    /**
     * Acquires a permit from this semaphore, if one becomes available 
     * within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     * <p>If no permit is available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If a permit is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire
     * a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * If the time is less than or equal to zero, the method will not wait 
     * at all.
     *
     * @param timeout the maximum time to wait for a permit
     * @param unit the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * if the waiting time elapsed before a permit was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) 
        throws InterruptedException {
        return sync.acquireSharedTimed(1, unit.toNanos(timeout));
    }

    /**
     * Releases a permit, returning it to the semaphore.
     * <p>Releases a permit, increasing the number of available permits
     * by one.
     * If any threads are blocking trying to acquire a permit, then one
     * is selected and given the permit that was just released.
     * That thread is re-enabled for thread scheduling purposes.
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    public void release() {
        sync.releaseShared(1);
    }
       
    /**
     * Acquires the given number of permits from this semaphore, 
     * blocking until all are available, 
     * or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>Acquires the given number of permits, if they are available,
     * and returns immediately,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared. 
     * Any permits that were to be assigned to this thread are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * @param permits the number of permits to acquire
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }

    /**
     * Acquires the given number of permits from this semaphore, 
     * blocking until all are available.
     *
     * <p>Acquires the given number of permits, if they are available,
     * and returns immediately,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request.
     *
     * <p>If the current thread
     * is {@link Thread#interrupt interrupted} while waiting
     * for permits then it will continue to wait and its position in the
     * queue is not affected. When the
     * thread does return from this method its interrupt status will be set.
     *
     * @param permits the number of permits to acquire
     * @throws IllegalArgumentException if permits less than zero.
     *
     */
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedUninterruptibly(permits);
    }

    /**
     * Acquires the given number of permits from this semaphore, only if 
     * all are available at the  time of invocation.
     * <p>Acquires the given number of permits, if they are available, and 
     * returns immediately, with the value <tt>true</tt>,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then this method will return
     * immediately with the value <tt>false</tt> and the number of available
     * permits is unchanged.
     *
     * @param permits the number of permits to acquire
     *
     * @return <tt>true</tt> if the permits were acquired and <tt>false</tt>
     * otherwise.
     * @throws IllegalArgumentException if permits less than zero.
     */
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.acquireSharedState(false, permits, null) >= 0;
    }

    /**
     * Acquires the given number of permits from this semaphore, if all 
     * become available within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires the given number of permits, if they are available and 
     * returns immediately, with the value <tt>true</tt>,
     * reducing the number of available permits by the given amount.
     * <p>If insufficient permits are available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the permits are acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire
     * the permits,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * Any permits that were to be assigned to this thread, are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * If the time is
     * less than or equal to zero, the method will not wait at all.
     * Any permits that were to be assigned to this thread, are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits
     * @param unit the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if all permits were acquired and <tt>false</tt>
     * if the waiting time elapsed before all permits were acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        return sync.acquireSharedTimed(permits, unit.toNanos(timeout));
    }


    /**
     * Releases the given number of permits, returning them to the semaphore.
     * <p>Releases the given number of permits, increasing the number of 
     * available permits by that amount.
     * If any threads are blocking trying to acquire permits, then the
     * one that has been waiting the longest
     * is selected and given the permits that were just released.
     * If the number of available permits satisfies that thread's request
     * then that thread is re-enabled for thread scheduling purposes; otherwise
     * the thread continues to wait. If there are still permits available
     * after the first thread's request has been satisfied, then those permits
     * are assigned to the next waiting thread. If it is satisfied then it is
     * re-enabled for thread scheduling purposes. This continues until there
     * are insufficient permits to satisfy the next waiting thread, or there
     * are no more waiting threads.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link Semaphore#acquire acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     *
     * @param permits the number of permits to release
     * @throws IllegalArgumentException if permits less than zero.
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    /**
     * Return the current number of permits available in this semaphore.
     * <p>This method is typically used for debugging and testing purposes.
     * @return the number of permits available in this semaphore.
     */
    public int availablePermits() {
        return sync.getState().get();
    }

    /**
     * Shrink the number of available permits by the indicated
     * reduction. This method can be useful in subclasses that
     * use semaphores to track available resources that become
     * unavailable. This method differs from <tt>acquire</tt>
     * in that it does not block waiting for permits to become
     * available.
     * @param reduction the number of permits to remove
     * @throws IllegalArgumentException if reduction is negative
     */
    protected void reducePermits(int reduction) {
	if (reduction < 0) throw new IllegalArgumentException();
        sync.getState().getAndAdd(-reduction);
    }

    /**
     * Return true if this semaphore has fairness set true.
     * @return true if this semaphore has fairness set true.
     */
    public boolean isFair() {
        return sync.fair;
    }


    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations may occur at any time, a <tt>true</tt>
     * return does not guarantee that any other thread will ever
     * acquire.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return true if there may be other threads waiting to acquire
     * the lock.
     */
    public final boolean hasWaiters() { 
        return sync.hasWaiters();
    }


    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
    
}
