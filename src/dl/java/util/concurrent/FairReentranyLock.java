package java.util.concurrent;

/**
 * A reentrant mutual exclusion lock that, under contention, favors
 * granting access to the longest-waiting thread.  Programs using fair
 * locks may display lower overall throughput (i.e., are slower) than
 * those using default locks, but have but smaller variances in times
 * to obtain locks.
 */

public class FairReentrantLock extends ReentrantLock {
    /**
     * Creates an instance of <tt>FairReentrantLock</tt>.
     */
    public FairReentrantLock() { }

    /**
     * Return true if it is OK to take fast path to lock.  For fair
     * locks, we allow barging only when there are no waiters.
     */
    boolean canBarge() {
        return queueEmpty();
    }
}



