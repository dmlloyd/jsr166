/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;

/**
 * An implementation of {@link ReadWriteLock} supporting similar
 * semantics to {@link ReentrantLock}.
 * <p>This class has the following properties:
 *
 * <ul>
 * <li><b>Acquisition order</b>
 *
 * <p> This class does not impose a reader or writer preference
 * ordering for lock access.  However, it does support an optional
 * <em>fairness</em> policy.  When constructed as fair, threads
 * contend using an approximately arrival-order policy. When the write
 * lock is released either longest-waiting single writer will be
 * assigned the write lock, or if there is a reader waiting longer
 * than any writer, the set of readers will be assigned the read lock.
 * <p>If readers are active and a writer arrives then no subsequent
 * readers will be granted the read lock until after that writer has
 * acquired and released the write lock.
 * 
 * <li><b>Reentrancy</b>
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Readers are not
 * allowed until all write locks held by the writing thread have been
 * released.  
 * <p>Additionally, a writer can acquire the read lock - but not vice-versa.
 * Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that
 * perform reads under read locks. 
 * If a reader tries to acquire the write lock it will never succeed.
 * 
 * <li><b>Lock downgrading</b>
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. However, upgrading from a read lock to the write lock, is
 * <b>not</b> possible.
 *
 * <li><b>Interruption of lock acquisition</b>
 * <p>The read lock and write lock both support interruption during lock
 * acquisition.
 * And both view the
 * interruptible lock methods as explicit interruption points and give
 * preference to responding to the interrupt over normal or reentrant 
 * acquisition of the lock, or over reporting the elapse of the waiting
 * time, as applicable.

 *
 * <li><b>{@link Condition} support</b>
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the 
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 * <p>The read lock does not support a {@link Condition} and
 * <tt>readLock().newCondition()</tt> throws 
 * <tt>UnsupportedOperationException</tt>.
 *
 * <li><b>Ownership</b>
 * <p>While not exposing a means to query the owner, the write lock does
 * internally define an owner and so the write lock can only be released by
 * the thread that acquired it.
 * <p>In contrast, the read lock has no concept of ownership.
 * Consequently, while not a particularly 
 * good practice, it is possible to acquire a read lock in one thread, and 
 * release it in another. This can occasionally be useful.
 *
 *
 * </ul>
 *
 * <p><b>Sample usage</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache (exception
 * handling is elided for simplicity):
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *        // upgrade lock manually
 *        rwl.readLock().unlock();   // must unlock first to obtain writelock
 *        rwl.writeLock().lock();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().lock();  // reacquire read without giving up write lock
 *        rwl.writeLock().unlock(); // unlock write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().unlock();
 *   }
 * }
 * </pre>
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
 * @author Doug Lea
 *
 */
public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable  {

    /*
      Note that all fields are transient and defined in a way that
      deserialized locks are in initial unlocked state.
    */

    /** 
     * Main lock.  Writers acquire on entry, and hold until release.
     * Reentrant acquires on write lock are allowed.  Readers acquire
     * and release only during entry, but are blocked from doing so if
     * there is an active writer. 
     **/
    private final ReentrantLock entryLock;

    /** 
     * Number of threads that have entered read lock.  This is never
     * reset to zero. Incremented only during acquisition of read lock
     * while the "entryLock" is held, but read elsewhere, so is declared
     * volatile.
     **/
    private transient volatile int readers;

    /** 
     * Number of threads that have exited read lock.  This is never
     * reset to zero.  Accessed only in code protected by rLock. When
     * exreaders != readers, the rwlock is being used for
     * reading. Else if the entry lock is held, it is being used for
     * writing (or in transition). Else it is free.  Note: To
     * distinguish these states, we assume that fewer than 2^32 reader
     * threads can simultaneously execute.
     **/
    private transient int exreaders;

    /**
     * Boolean set to true when a writer is waiting to enter.
     * Needed only by ReaderLock.tryLock 
     */
    private transient volatile boolean waitingWriter;

    /**
     * Lock used by exiting readers and entering writers
     */

    private final ReentrantLock writeCheckLock = new ReentrantLock();

    /**
     * Condition waited on by blocked entering writers.
     */
    private final Condition writeEnabled = writeCheckLock.newCondition();

    /**
     * Reader exit protocol, called from unlock. if this is the last
     * reader, notify a possibly waiting writer.  Because waits occur
     * only when entry lock is held, at most one writer can be waiting
     * for this notification.  Because increments to "readers" aren't
     * protected by "this" lock, the notification may be spurious
     * (when an incoming reader in in the process of updating the
     * field), but at the point tested in acquiring write lock, both
     * locks will be held, thus avoiding false alarms. And we will
     * never miss an opportunity to send a notification when it is
     * actually needed.
    */
    private void readerExit() {
        writeCheckLock.lock();
        try {
            if (++exreaders == readers) 
                writeEnabled.signal(); 
        }
        finally {
            writeCheckLock.unlock();
        }
    }

    /**
     * Writer enter protocol, called after acquiring entry lock.
     * Wait until all readers have exited.
     * @throws InterruptedException if interrupted while waiting
    */
    private void writerEnter() throws InterruptedException {
        writeCheckLock.lock();
        try {
            while (exreaders != readers) {
                waitingWriter = true;
                writeEnabled.await();
                waitingWriter = false;
            }
        }
        finally {
            writeCheckLock.unlock();
        }
    }

    /**
     * Trylock version of Writer enter protocol
     * @return true if successful
     */
    private boolean tryWriterEnter() {
        writeCheckLock.lock();
        try {
            boolean ok = (exreaders == readers);
            return ok;
        }
        finally {
            writeCheckLock.unlock();
        }
    }

    /**
     * Timed version of writer enter protocol, called after acquiring
     * entry lock.  Wait until all readers have exited.
     * @param nanos the wait time
     * @return true if successful
     * @throws InterruptedException if interrupted while waiting
     */
    private boolean tryWriterEnter(long nanos) throws InterruptedException {
        writeCheckLock.lock();
        try {
            while (exreaders != readers) {
                if (nanos <= 0) 
                    return false;
                waitingWriter = true;
                nanos = writeEnabled.awaitNanos(nanos);
                waitingWriter = false;
            }
            return true;
        }
        finally {
            writeCheckLock.unlock();
        }
    }

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * default ordering properties.
     */
    public ReentrantReadWriteLock() {
        entryLock = new ReentrantLock();
    }

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * the given fairness policy
     * @param fair true if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        entryLock = new ReentrantLock(fair);
    }

    /** Inner class providing readlock */
    private final Lock readerLock = new ReaderLock();
    /** Inner class providing writelock */
    private final Lock writerLock = new WriterLock();

    public Lock writeLock() { return writerLock; }

    public Lock readLock() { return readerLock; }

    /**
     * The Reader lock
     */
    private class ReaderLock implements Lock {

        public void lock() {
            entryLock.lock();
            try {
                ++readers; 
            }
            finally {
                entryLock.unlock();
            }
        }

        public void lockInterruptibly() throws InterruptedException {
            entryLock.lockInterruptibly();
            try {
                ++readers; 
            }
            finally {
                entryLock.unlock();
            }
        }

        public  boolean tryLock() {
            // if we fail entry lock just due to contention, try again.
            while (!entryLock.tryLock()) {
                // fail if a writer present
                if (exreaders == readers || waitingWriter)
                    return false;
                else
                    Thread.yield();
            }
            try {
                ++readers; 
                return true;
            }
            finally {
                entryLock.unlock();
            }
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            if (!entryLock.tryLock(time, unit)) 
                return false;
            try {
                ++readers; 
                return true;
            }
            finally {
                entryLock.unlock();
            }
        }

        public  void unlock() {
            readerExit();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The writer lock
     */
    private class WriterLock implements Lock  {
        public void lock() {
            entryLock.lock();
            boolean wasInterrupted = false;
            for (;;) {
                try {
                    writerEnter();
                    break;
                }
                catch (InterruptedException ie) {
                    wasInterrupted = true;
                }
            }
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }

        public void lockInterruptibly() throws InterruptedException {
            entryLock.lockInterruptibly();
            try {
                writerEnter();
            }
            catch (InterruptedException ie) {
                entryLock.unlock();
                throw ie;
            }
        }

        public boolean tryLock( ) {
            if (!entryLock.tryLock())
                return false;
            if (entryLock.getHoldCount() > 1) 
                return true;

            if (!tryWriterEnter()) {
                entryLock.unlock();
                return false;
            }
            return true;
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            long startTime = System.nanoTime();
            long nanos = unit.toNanos(time);
            if (!entryLock.tryLock(nanos, TimeUnit.NANOSECONDS)) 
                return false;
            if (entryLock.getHoldCount() > 1) 
                return true;
            
            nanos -= System.nanoTime() - startTime;
            try {
                if (!tryWriterEnter(nanos)) {
                    entryLock.unlock();
                    return false;
                }
            }
            catch (InterruptedException ie) {
                entryLock.unlock();
                
                throw ie;
            }
            return true;
        }
        
        public void unlock() {
            entryLock.unlock();
        }

        public Condition newCondition() { 
            return entryLock.newCondition();
        }
        
    }

}

