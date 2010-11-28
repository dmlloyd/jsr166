/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * A reusable synchronization barrier, similar in functionality to
 * {@link java.util.concurrent.CyclicBarrier CyclicBarrier} and
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * but supporting more flexible usage.
 *
 * <p> <b>Registration.</b> Unlike the case for other barriers, the
 * number of parties <em>registered</em> to synchronize on a Phaser
 * may vary over time.  Tasks may be registered at any time (using
 * methods {@link #register}, {@link #bulkRegister}, or forms of
 * constructors establishing initial numbers of parties), and
 * optionally deregistered upon any arrival (using {@link
 * #arriveAndDeregister}).  As is the case with most basic
 * synchronization constructs, registration and deregistration affect
 * only internal counts; they do not establish any further internal
 * bookkeeping, so tasks cannot query whether they are registered.
 * (However, you can introduce such bookkeeping by subclassing this
 * class.)
 *
 * <p> <b>Synchronization.</b> Like a {@code CyclicBarrier}, a {@code
 * Phaser} may be repeatedly awaited.  Method {@link
 * #arriveAndAwaitAdvance} has effect analogous to {@link
 * java.util.concurrent.CyclicBarrier#await CyclicBarrier.await}. Each
 * generation of a {@code Phaser} has an associated phase number. The
 * phase number starts at zero, and advances when all parties arrive
 * at the barrier, wrapping around to zero after reaching {@code
 * Integer.MAX_VALUE}. The use of phase numbers enables independent
 * control of actions upon arrival at a barrier and upon awaiting
 * others, via two kinds of methods that may be invoked by any
 * registered party:
 *
 * <ul>
 *
 *   <li> <b>Arrival.</b> Methods {@link #arrive} and
 *       {@link #arriveAndDeregister} record arrival at a
 *       barrier. These methods do not block, but return an associated
 *       <em>arrival phase number</em>; that is, the phase number of
 *       the barrier to which the arrival applied. When the final
 *       party for a given phase arrives, an optional barrier action
 *       is performed and the phase advances.  Barrier actions,
 *       performed by the party triggering a phase advance, are
 *       arranged by overriding method {@link #onAdvance(int, int)},
 *       which also controls termination. Overriding this method is
 *       similar to, but more flexible than, providing a barrier
 *       action to a {@code CyclicBarrier}.
 *
 *   <li> <b>Waiting.</b> Method {@link #awaitAdvance} requires an
 *       argument indicating an arrival phase number, and returns when
 *       the barrier advances to (or is already at) a different phase.
 *       Unlike similar constructions using {@code CyclicBarrier},
 *       method {@code awaitAdvance} continues to wait even if the
 *       waiting thread is interrupted. Interruptible and timeout
 *       versions are also available, but exceptions encountered while
 *       tasks wait interruptibly or with timeout do not change the
 *       state of the barrier. If necessary, you can perform any
 *       associated recovery within handlers of those exceptions,
 *       often after invoking {@code forceTermination}.  Phasers may
 *       also be used by tasks executing in a {@link ForkJoinPool},
 *       which will ensure sufficient parallelism to execute tasks
 *       when others are blocked waiting for a phase to advance.
 *
 * </ul>
 *
 * <p> <b>Termination.</b> A {@code Phaser} may enter a
 * <em>termination</em> state in which all synchronization methods
 * immediately return without updating Phaser state or waiting for
 * advance, and indicating (via a negative phase value) that execution
 * is complete.  Termination is triggered when an invocation of {@code
 * onAdvance} returns {@code true}. The default implementation returns
 * {@code true} if a deregistration has caused the number of
 * registered parties to become zero.  As illustrated below, when
 * Phasers control actions with a fixed number of iterations, it is
 * often convenient to override this method to cause termination when
 * the current phase number reaches a threshold. Method {@link
 * #forceTermination} is also available to abruptly release waiting
 * threads and allow them to terminate.
 *
 * <p> <b>Tiering.</b> Phasers may be <em>tiered</em> (i.e.,
 * constructed in tree structures) to reduce contention. Phasers with
 * large numbers of parties that would otherwise experience heavy
 * synchronization contention costs may instead be set up so that
 * groups of sub-phasers share a common parent.  This may greatly
 * increase throughput even though it incurs greater per-operation
 * overhead.
 *
 * <p><b>Monitoring.</b> While synchronization methods may be invoked
 * only by registered parties, the current state of a Phaser may be
 * monitored by any caller.  At any given moment there are {@link
 * #getRegisteredParties} parties in total, of which {@link
 * #getArrivedParties} have arrived at the current phase ({@link
 * #getPhase}).  When the remaining ({@link #getUnarrivedParties})
 * parties arrive, the phase advances.  The values returned by these
 * methods may reflect transient states and so are not in general
 * useful for synchronization control.  Method {@link #toString}
 * returns snapshots of these state queries in a form convenient for
 * informal monitoring.
 *
 * <p><b>Sample usages:</b>
 *
 * <p>A {@code Phaser} may be used instead of a {@code CountDownLatch}
 * to control a one-shot action serving a variable number of parties.
 * The typical idiom is for the method setting this up to first
 * register, then start the actions, then deregister, as in:
 *
 *  <pre> {@code
 * void runTasks(List<Runnable> tasks) {
 *   final Phaser phaser = new Phaser(1); // "1" to register self
 *   // create and start threads
 *   for (Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         phaser.arriveAndAwaitAdvance(); // await all creation
 *         task.run();
 *       }
 *     }.start();
 *   }
 *
 *   // allow threads to start and deregister self
 *   phaser.arriveAndDeregister();
 * }}</pre>
 *
 * <p>One way to cause a set of threads to repeatedly perform actions
 * for a given number of iterations is to override {@code onAdvance}:
 *
 *  <pre> {@code
 * void startTasks(List<Runnable> tasks, final int iterations) {
 *   final Phaser phaser = new Phaser() {
 *     protected boolean onAdvance(int phase, int registeredParties) {
 *       return phase >= iterations || registeredParties == 0;
 *     }
 *   };
 *   phaser.register();
 *   for (final Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         do {
 *           task.run();
 *           phaser.arriveAndAwaitAdvance();
 *         } while (!phaser.isTerminated());
 *       }
 *     }.start();
 *   }
 *   phaser.arriveAndDeregister(); // deregister self, don't wait
 * }}</pre>
 *
 * If the main task must later await termination, it
 * may re-register and then execute a similar loop:
 *  <pre> {@code
 *   // ...
 *   phaser.register();
 *   while (!phaser.isTerminated())
 *     phaser.arriveAndAwaitAdvance();}</pre>
 *
 * <p>Related constructions may be used to await particular phase numbers
 * in contexts where you are sure that the phase will never wrap around
 * {@code Integer.MAX_VALUE}. For example:
 *
 *  <pre> {@code
 * void awaitPhase(Phaser phaser, int phase) {
 *   int p = phaser.register(); // assumes caller not already registered
 *   while (p < phase) {
 *     if (phaser.isTerminated())
 *       // ... deal with unexpected termination
 *     else
 *       p = phaser.arriveAndAwaitAdvance();
 *   }
 *   phaser.arriveAndDeregister();
 * }}</pre>
 *
 *
 * <p>To create a set of tasks using a tree of Phasers,
 * you could use code of the following form, assuming a
 * Task class with a constructor accepting a Phaser that
 * it registers with upon construction:
 *
 *  <pre> {@code
 * void build(Task[] actions, int lo, int hi, Phaser ph) {
 *   if (hi - lo > TASKS_PER_PHASER) {
 *     for (int i = lo; i < hi; i += TASKS_PER_PHASER) {
 *       int j = Math.min(i + TASKS_PER_PHASER, hi);
 *       build(actions, i, j, new Phaser(ph));
 *     }
 *   } else {
 *     for (int i = lo; i < hi; ++i)
 *       actions[i] = new Task(ph);
 *       // assumes new Task(ph) performs ph.register()
 *   }
 * }
 * // .. initially called, for n tasks via
 * build(new Task[n], 0, n, new Phaser());}</pre>
 *
 * The best value of {@code TASKS_PER_PHASER} depends mainly on
 * expected barrier synchronization rates. A value as low as four may
 * be appropriate for extremely small per-barrier task bodies (thus
 * high rates), or up to hundreds for extremely large ones.
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of parties to 65535. Attempts to register additional
 * parties result in {@code IllegalStateException}. However, you can and
 * should create tiered Phasers to accommodate arbitrarily large sets
 * of participants.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class Phaser {
    /*
     * This class implements an extension of X10 "clocks".  Thanks to
     * Vijay Saraswat for the idea, and to Vivek Sarkar for
     * enhancements to extend functionality.
     */

    /**
     * Barrier state representation. Conceptually, a barrier contains
     * four values:
     *
     * * unarrived -- the number of parties yet to hit barrier (bits  0-15)
     * * parties -- the number of parties to wait              (bits 16-31)
     * * phase -- the generation of the barrier                (bits 32-62)
     * * terminated -- set if barrier is terminated            (bit  63 / sign)
     *
     * However, to efficiently maintain atomicity, these values are
     * packed into a single (atomic) long. Termination uses the sign
     * bit of 32 bit representation of phase, so phase is set to -1 on
     * termination. Good performance relies on keeping state decoding
     * and encoding simple, and keeping race windows short.
     */
    private volatile long state;

    private static final int  MAX_PARTIES     = 0xffff;
    private static final int  MAX_PHASE       = 0x7fffffff;
    private static final int  PARTIES_SHIFT   = 16;
    private static final int  PHASE_SHIFT     = 32;
    private static final int  UNARRIVED_MASK  = 0xffff;      // to mask ints
    private static final long PARTIES_MASK    = 0xffff0000L; // to mask longs
    private static final long ONE_ARRIVAL     = 1L;
    private static final long ONE_PARTY       = 1L << PARTIES_SHIFT;
    private static final long TERMINATION_BIT = 1L << 63;

    // The following unpacking methods are usually manually inlined

    private static int unarrivedOf(long s) {
        return (int)s & UNARRIVED_MASK;
    }

    private static int partiesOf(long s) {
        return (int)s >>> PARTIES_SHIFT;
    }

    private static int phaseOf(long s) {
        return (int) (s >>> PHASE_SHIFT);
    }

    private static int arrivedOf(long s) {
        return partiesOf(s) - unarrivedOf(s);
    }

    /**
     * The parent of this phaser, or null if none
     */
    private final Phaser parent;

    /**
     * The root of phaser tree. Equals this if not in a tree.  Used to
     * support faster state push-down.
     */
    private final Phaser root;

    /**
     * Heads of Treiber stacks for waiting threads. To eliminate
     * contention when releasing some threads while adding others, we
     * use two of them, alternating across even and odd phases.
     * Subphasers share queues with root to speed up releases.
     */
    private final AtomicReference<QNode> evenQ;
    private final AtomicReference<QNode> oddQ;

    private AtomicReference<QNode> queueFor(int phase) {
        return ((phase & 1) == 0) ? evenQ : oddQ;
    }

    /**
     * Returns message string for bounds exceptions on arrival.
     */
    private String badArrive(long s) {
        return "Attempted arrival of unregistered party for " +
            stateToString(s);
    }

    /**
     * Returns message string for bounds exceptions on registration.
     */
    private String badRegister(long s) {
        return "Attempt to register more than " +
            MAX_PARTIES + " parties for " + stateToString(s);
    }

    /**
     * Main implementation for methods arrive and arriveAndDeregister.
     * Manually tuned to speed up and minimize race windows for the
     * common case of just decrementing unarrived field.
     *
     * @param adj - adjustment to apply to state -- either
     * ONE_ARRIVAL (for arrive) or
     * ONE_ARRIVAL|ONE_PARTY (for arriveAndDeregister)
     */
    private int doArrive(long adj) {
        for (;;) {
            long s = state;
            int unarrived = (int)s & UNARRIVED_MASK;
            int phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                return phase;
            else if (unarrived == 0) {
                if (reconcileState() == s)     // recheck
                    throw new IllegalStateException(badArrive(s));
            }
            else if (UNSAFE.compareAndSwapLong(this, stateOffset, s, s-=adj)) {
                if (unarrived == 1) {
                    long p = s & PARTIES_MASK; // unshifted parties field
                    long lu = p >>> PARTIES_SHIFT;
                    int u = (int)lu;
                    int nextPhase = (phase + 1) & MAX_PHASE;
                    long next = ((long)nextPhase << PHASE_SHIFT) | p | lu;
                    final Phaser parent = this.parent;
                    if (parent == null) {
                        if (onAdvance(phase, u))
                            next |= TERMINATION_BIT;
                        UNSAFE.compareAndSwapLong(this, stateOffset, s, next);
                        releaseWaiters(phase);
                    }
                    else {
                        parent.doArrive((u == 0) ?
                                        ONE_ARRIVAL|ONE_PARTY : ONE_ARRIVAL);
                        if ((int)(parent.state >>> PHASE_SHIFT) != nextPhase ||
                            ((int)(state >>> PHASE_SHIFT) != nextPhase &&
                             !UNSAFE.compareAndSwapLong(this, stateOffset,
                                                        s, next)))
                            reconcileState();
                    }
                }
                return phase;
            }
        }
    }

    /**
     * Implementation of register, bulkRegister
     *
     * @param registrations number to add to both parties and
     * unarrived fields. Must be greater than zero.
     */
    private int doRegister(int registrations) {
        // adjustment to state
        long adj = ((long)registrations << PARTIES_SHIFT) | registrations;
        final Phaser parent = this.parent;
        for (;;) {
            long s = (parent == null) ? state : reconcileState();
            int parties = (int)s >>> PARTIES_SHIFT;
            int phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                return phase;
            else if (registrations > MAX_PARTIES - parties)
                throw new IllegalStateException(badRegister(s));
            else if ((parties == 0 && parent == null) || // first reg of root
                     ((int)s & UNARRIVED_MASK) != 0) {   // not advancing
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, s + adj))
                    return phase;
            }
            else if (parties != 0)               // wait for onAdvance
                root.internalAwaitAdvance(phase, null);
            else {                               // 1st registration of child
                synchronized (this) {            // register parent first
                    if (reconcileState() == s) { // recheck under lock
                        parent.doRegister(1);    // OK if throws IllegalState
                        for (;;) {               // simpler form of outer loop
                            s = reconcileState();
                            phase = (int)(s >>> PHASE_SHIFT);
                            if (phase < 0 ||
                                UNSAFE.compareAndSwapLong(this, stateOffset,
                                                          s, s + adj))
                                return phase;
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively resolves lagged phase propagation from root if necessary.
     */
    private long reconcileState() {
        Phaser par = parent;
        long s = state;
        if (par != null) {
            Phaser rt = root;
            int phase, rPhase;
            while ((phase = (int)(s >>> PHASE_SHIFT)) >= 0 &&
                   (rPhase = (int)(rt.state >>> PHASE_SHIFT)) != phase) {
                if ((int)(par.state >>> PHASE_SHIFT) != rPhase)
                    par.reconcileState();
                else if (rPhase < 0 || ((int)s & UNARRIVED_MASK) == 0) {
                    long u = s & PARTIES_MASK; // reset unarrived to parties
                    long next = ((((long) rPhase) << PHASE_SHIFT) | u |
                                 (u >>> PARTIES_SHIFT));
                    UNSAFE.compareAndSwapLong(this, stateOffset, s, next);
                }
                s = state;
            }
        }
        return s;
    }

    /**
     * Creates a new Phaser without any initially registered parties,
     * initial phase number 0, and no parent. Any thread using this
     * Phaser will need to first register for it.
     */
    public Phaser() {
        this(null, 0);
    }

    /**
     * Creates a new Phaser with the given number of registered
     * unarrived parties, initial phase number 0, and no parent.
     *
     * @param parties the number of parties required to trip barrier
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(int parties) {
        this(null, parties);
    }

    /**
     * Equivalent to {@link #Phaser(Phaser, int) Phaser(parent, 0)}.
     *
     * @param parent the parent Phaser
     */
    public Phaser(Phaser parent) {
        this(parent, 0);
    }

    /**
     * Creates a new Phaser with the given parent and number of
     * registered unarrived parties. Registration and deregistration
     * of this child Phaser with its parent are managed automatically.
     * If the given parent is non-null, whenever this child Phaser has
     * any registered parties (as established in this constructor,
     * {@link #register}, or {@link #bulkRegister}), this child Phaser
     * is registered with its parent. Whenever the number of
     * registered parties becomes zero as the result of an invocation
     * of {@link #arriveAndDeregister}, this child Phaser is
     * deregistered from its parent.
     *
     * @param parent the parent Phaser
     * @param parties the number of parties required to trip barrier
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(Phaser parent, int parties) {
        if (parties >>> PARTIES_SHIFT != 0)
            throw new IllegalArgumentException("Illegal number of parties");
        long s = ((long) parties) | (((long) parties) << PARTIES_SHIFT);
        this.parent = parent;
        if (parent != null) {
            Phaser r = parent.root;
            this.root = r;
            this.evenQ = r.evenQ;
            this.oddQ = r.oddQ;
            if (parties != 0)
                s |= ((long)(parent.doRegister(1))) << PHASE_SHIFT;
        }
        else {
            this.root = this;
            this.evenQ = new AtomicReference<QNode>();
            this.oddQ = new AtomicReference<QNode>();
        }
        this.state = s;
    }

    /**
     * Adds a new unarrived party to this Phaser.  If an ongoing
     * invocation of {@link #onAdvance} is in progress, this method
     * may await its completion before returning.  If this Phaser has
     * a parent, and this Phaser previously had no registered parties,
     * this Phaser is also registered with its parent.
     *
     * @return the arrival phase number to which this registration applied
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     */
    public int register() {
        return doRegister(1);
    }

    /**
     * Adds the given number of new unarrived parties to this Phaser.
     * If an ongoing invocation of {@link #onAdvance} is in progress,
     * this method may await its completion before returning.  If this
     * Phaser has a parent, and the given number of parities is
     * greater than zero, and this Phaser previously had no registered
     * parties, this Phaser is also registered with its parent.
     *
     * @param parties the number of additional parties required to trip barrier
     * @return the arrival phase number to which this registration applied
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     * @throws IllegalArgumentException if {@code parties < 0}
     */
    public int bulkRegister(int parties) {
        if (parties < 0)
            throw new IllegalArgumentException();
        if (parties == 0)
            return getPhase();
        return doRegister(parties);
    }

    /**
     * Arrives at the barrier, without waiting for others to arrive.
     *
     * <p>It is a usage error for an unregistered party to invoke this
     * method.  However, this error may result in an {@code
     * IllegalStateException} only upon some subsequent operation on
     * this Phaser, if ever.
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arrive() {
        return doArrive(ONE_ARRIVAL);
    }

    /**
     * Arrives at the barrier and deregisters from it without waiting
     * for others to arrive. Deregistration reduces the number of
     * parties required to trip the barrier in future phases.  If this
     * Phaser has a parent, and deregistration causes this Phaser to
     * have zero parties, this Phaser is also deregistered from its
     * parent.
     *
     * <p>It is a usage error for an unregistered party to invoke this
     * method.  However, this error may result in an {@code
     * IllegalStateException} only upon some subsequent operation on
     * this Phaser, if ever.
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of registered or unarrived parties would become negative
     */
    public int arriveAndDeregister() {
        return doArrive(ONE_ARRIVAL|ONE_PARTY);
    }

    /**
     * Arrives at the barrier and awaits others. Equivalent in effect
     * to {@code awaitAdvance(arrive())}.  If you need to await with
     * interruption or timeout, you can arrange this with an analogous
     * construction using one of the other forms of the {@code
     * awaitAdvance} method.  If instead you need to deregister upon
     * arrival, use {@code awaitAdvance(arriveAndDeregister())}.
     *
     * <p>It is a usage error for an unregistered party to invoke this
     * method.  However, this error may result in an {@code
     * IllegalStateException} only upon some subsequent operation on
     * this Phaser, if ever.
     *
     * @return the arrival phase number, or a negative number if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arriveAndAwaitAdvance() {
        return awaitAdvance(arrive());
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value, returning immediately if the current phase of the
     * barrier is not equal to the given phase value or this barrier
     * is terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     */
    public int awaitAdvance(int phase) {
        Phaser r;
        int p = (int)(state >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase &&
            (p = (int)((r = root).state >>> PHASE_SHIFT)) == phase)
            return r.internalAwaitAdvance(phase, null);
        return p;
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value, throwing {@code InterruptedException} if interrupted
     * while waiting, or returning immediately if the current phase of
     * the barrier is not equal to the given phase value or this
     * barrier is terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     * @throws InterruptedException if thread interrupted while waiting
     */
    public int awaitAdvanceInterruptibly(int phase)
        throws InterruptedException {
        Phaser r;
        int p = (int)(state >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase &&
            (p = (int)((r = root).state >>> PHASE_SHIFT)) == phase) {
            QNode node = new QNode(this, phase, true, false, 0L);
            p = r.internalAwaitAdvance(phase, node);
            if (node.wasInterrupted)
                throw new InterruptedException();
        }
        return p;
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value or the given timeout to elapse, throwing {@code
     * InterruptedException} if interrupted while waiting, or
     * returning immediately if the current phase of the barrier is
     * not equal to the given phase value or this barrier is
     * terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     * @throws InterruptedException if thread interrupted while waiting
     * @throws TimeoutException if timed out while waiting
     */
    public int awaitAdvanceInterruptibly(int phase,
                                         long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        Phaser r;
        int p = (int)(state >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase &&
            (p = (int)((r = root).state >>> PHASE_SHIFT)) == phase) {
            QNode node = new QNode(this, phase, true, true, nanos);
            p = r.internalAwaitAdvance(phase, node);
            if (node.wasInterrupted)
                throw new InterruptedException();
            else if (p == phase)
                throw new TimeoutException();
        }
        return p;
    }

    /**
     * Forces this barrier to enter termination state.  Counts of
     * arrived and registered parties are unaffected.  If this Phaser
     * is a member of a tiered set of Phasers, then all of the Phasers
     * in the set are terminated.  If this Phaser is already
     * terminated, this method has no effect.  This method may be
     * useful for coordinating recovery after one or more tasks
     * encounter unexpected exceptions.
     */
    public void forceTermination() {
        // Only need to change root state
        final Phaser root = this.root;
        long s;
        while ((s = root.state) >= 0) {
            if (UNSAFE.compareAndSwapLong(root, stateOffset,
                                          s, s | TERMINATION_BIT)) {
                releaseWaiters(0); // signal all threads
                releaseWaiters(1);
                return;
            }
        }
    }

    /**
     * Returns the current phase number. The maximum phase number is
     * {@code Integer.MAX_VALUE}, after which it restarts at
     * zero. Upon termination, the phase number is negative.
     *
     * @return the phase number, or a negative value if terminated
     */
    public final int getPhase() {
        return (int)(root.state >>> PHASE_SHIFT);
    }

    /**
     * Returns the number of parties registered at this barrier.
     *
     * @return the number of parties
     */
    public int getRegisteredParties() {
        return partiesOf(state);
    }

    /**
     * Returns the number of registered parties that have arrived at
     * the current phase of this barrier.
     *
     * @return the number of arrived parties
     */
    public int getArrivedParties() {
        long s = state;
        int u = unarrivedOf(s); // only reconcile if possibly needed
        return (u != 0 || parent == null) ?
            partiesOf(s) - u :
            arrivedOf(reconcileState());
    }

    /**
     * Returns the number of registered parties that have not yet
     * arrived at the current phase of this barrier.
     *
     * @return the number of unarrived parties
     */
    public int getUnarrivedParties() {
        int u = unarrivedOf(state);
        return (u != 0 || parent == null) ? u : unarrivedOf(reconcileState());
    }

    /**
     * Returns the parent of this Phaser, or {@code null} if none.
     *
     * @return the parent of this Phaser, or {@code null} if none
     */
    public Phaser getParent() {
        return parent;
    }

    /**
     * Returns the root ancestor of this Phaser, which is the same as
     * this Phaser if it has no parent.
     *
     * @return the root ancestor of this Phaser
     */
    public Phaser getRoot() {
        return root;
    }

    /**
     * Returns {@code true} if this barrier has been terminated.
     *
     * @return {@code true} if this barrier has been terminated
     */
    public boolean isTerminated() {
        return root.state < 0L;
    }

    /**
     * Overridable method to perform an action upon impending phase
     * advance, and to control termination. This method is invoked
     * upon arrival of the party tripping the barrier (when all other
     * waiting parties are dormant).  If this method returns {@code
     * true}, then, rather than advance the phase number, this barrier
     * will be set to a final termination state, and subsequent calls
     * to {@link #isTerminated} will return true. Any (unchecked)
     * Exception or Error thrown by an invocation of this method is
     * propagated to the party attempting to trip the barrier, in
     * which case no advance occurs.
     *
     * <p>The arguments to this method provide the state of the Phaser
     * prevailing for the current transition.  The effects of invoking
     * arrival, registration, and waiting methods on this Phaser from
     * within {@code onAdvance} are unspecified and should not be
     * relied on.
     *
     * <p>If this Phaser is a member of a tiered set of Phasers, then
     * {@code onAdvance} is invoked only for its root Phaser on each
     * advance.
     *
     * <p>To support the most common use cases, the default
     * implementation of this method returns {@code true} when the
     * number of registered parties has become zero as the result of a
     * party invoking {@code arriveAndDeregister}.  You can disable
     * this behavior, thus enabling continuation upon future
     * registrations, by overriding this method to always return
     * {@code false}:
     *
     * <pre> {@code
     * Phaser phaser = new Phaser() {
     *   protected boolean onAdvance(int phase, int parties) { return false; }
     * }}</pre>
     *
     * @param phase the phase number on entering the barrier
     * @param registeredParties the current number of registered parties
     * @return {@code true} if this barrier should terminate
     */
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties <= 0;
    }

    /**
     * Returns a string identifying this Phaser, as well as its
     * state.  The state, in brackets, includes the String {@code
     * "phase = "} followed by the phase number, {@code "parties = "}
     * followed by the number of registered parties, and {@code
     * "arrived = "} followed by the number of arrived parties.
     *
     * @return a string identifying this barrier, as well as its state
     */
    public String toString() {
        return stateToString(reconcileState());
    }

    /**
     * Implementation of toString and string-based error messages
     */
    private String stateToString(long s) {
        return super.toString() +
            "[phase = " + phaseOf(s) +
            " parties = " + partiesOf(s) +
            " arrived = " + arrivedOf(s) + "]";
    }

    // Waiting mechanics

    /**
     * Removes and signals threads from queue for phase.
     */
    private void releaseWaiters(int phase) {
        AtomicReference<QNode> head = queueFor(phase);
        QNode q;
        int p;
        while ((q = head.get()) != null &&
               ((p = q.phase) == phase ||
                (int)(root.state >>> PHASE_SHIFT) != p)) {
            if (head.compareAndSet(q, q.next))
                q.signal();
        }
    }

    /** The number of CPUs, for spin control */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking while waiting for
     * advance, per arrival while waiting. On multiprocessors, fully
     * blocking and waking up a large number of threads all at once is
     * usually a very slow process, so we use rechargeable spins to
     * avoid it when threads regularly arrive: When a thread in
     * internalAwaitAdvance notices another arrival before blocking,
     * and there appear to be enough CPUs available, it spins
     * SPINS_PER_ARRIVAL more times before blocking. Plus, even on
     * uniprocessors, there is at least one intervening Thread.yield
     * before blocking. The value trades off good-citizenship vs big
     * unnecessary slowdowns.
     */
    static final int SPINS_PER_ARRIVAL = (NCPU < 2) ? 1 : 1 << 8;

    /**
     * Possibly blocks and waits for phase to advance unless aborted.
     * Call only from root node.
     *
     * @param phase current phase
     * @param node if non-null, the wait node to track interrupt and timeout;
     * if null, denotes noninterruptible wait
     * @return current phase
     */
    private int internalAwaitAdvance(int phase, QNode node) {
        boolean queued = false;      // true when node is enqueued
        int lastUnarrived = -1;      // to increase spins upon change
        int spins = SPINS_PER_ARRIVAL;
        long s;
        int p;
        while ((p = (int)((s = state) >>> PHASE_SHIFT)) == phase) {
            int unarrived = (int)s & UNARRIVED_MASK;
            if (unarrived != lastUnarrived) {
                if (lastUnarrived == -1) // ensure old queue clean
                    releaseWaiters(phase-1);
                if ((lastUnarrived = unarrived) < NCPU)
                    spins += SPINS_PER_ARRIVAL;
            }
            else if (spins > 0) {
                if (--spins == (SPINS_PER_ARRIVAL >>> 1))
                    Thread.yield();  // yield midway through spin
            }
            else if (node == null)   // must be noninterruptible
                node = new QNode(this, phase, false, false, 0L);
            else if (node.isReleasable()) {
                p = (int)(state >>> PHASE_SHIFT);
                break;               // aborted
            }
            else if (!queued) {      // push onto queue
                AtomicReference<QNode> head = queueFor(phase);
                QNode q = head.get();
                if (q == null || q.phase == phase) {
                    node.next = q;
                    if ((p = (int)(state >>> PHASE_SHIFT)) != phase)
                        break;       // recheck to avoid stale enqueue
                    else
                        queued = head.compareAndSet(q, node);
                }
            }
            else {
                try {
                    ForkJoinPool.managedBlock(node);
                } catch (InterruptedException ie) {
                    node.wasInterrupted = true;
                }
            }
        }

        if (node != null) {
            if (node.thread != null)
                node.thread = null; // disable unpark() in node.signal
            if (!node.interruptible && node.wasInterrupted)
                Thread.currentThread().interrupt();
        }
        if (p != phase)
            releaseWaiters(phase);
        return p;
    }

    /**
     * Wait nodes for Treiber stack representing wait queue
     */
    static final class QNode implements ForkJoinPool.ManagedBlocker {
        final Phaser phaser;
        final int phase;
        final boolean interruptible;
        final boolean timed;
        boolean wasInterrupted;
        long nanos;
        long lastTime;
        volatile Thread thread; // nulled to cancel wait
        QNode next;

        QNode(Phaser phaser, int phase, boolean interruptible,
              boolean timed, long nanos) {
            this.phaser = phaser;
            this.phase = phase;
            this.interruptible = interruptible;
            this.nanos = nanos;
            this.timed = timed;
            this.lastTime = timed? System.nanoTime() : 0L;
            thread = Thread.currentThread();
        }

        public boolean isReleasable() {
            Thread t = thread;
            if (t != null) {
                if (phaser.getPhase() != phase)
                    t = null;
                else {
                    if (Thread.interrupted())
                        wasInterrupted = true;
                    if (interruptible && wasInterrupted)
                        t = null;
                    else if (timed) {
                        if (nanos > 0) {
                            long now = System.nanoTime();
                            nanos -= now - lastTime;
                            lastTime = now;
                        }
                        if (nanos <= 0)
                            t = null;
                    }
                }
                if (t != null)
                    return false;
                thread = null;
            }
            return true;
        }

        public boolean block() {
            if (isReleasable())
                return true;
            else if (!timed)
                LockSupport.park(this);
            else if (nanos > 0)
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }

        void signal() {
            Thread t = thread;
            if (t != null) {
                thread = null;
                LockSupport.unpark(t);
            }
        }
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long stateOffset =
        objectFieldOffset("state", Phaser.class);

    private static long objectFieldOffset(String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }
}
