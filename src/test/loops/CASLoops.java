/*
 * Written by Doug Lea and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

/*
 * Estimates the difference in time for compareAndSet and CAS-like
 * operations versus unsynchronized, non-volatile pseudo-CAS when
 * updating random numbers. These estimates thus give the cost
 * of atomicity/barriers/exclusion over and above the time to
 * just compare and conditionally store (int) values, so are
 * not intended to measure the "raw" cost of a CAS.
 *
 * Outputs, for runs using 1 ... #cpus threads are, in nanoseconds:
 *  "Atomic CAS"      AtomicInteger.compareAndSet
 *  "Volatile"        pseudo-CAS using volatile store if comparison succeeds
 *  "Mutex"           emulated compare and set done under AQS-based mutex lock
 *  "Synchronized"    emulated compare and set done under a synchronized block.
 *
 * The last two are done only if this program is called with (any) argument
 */


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class CASLoops {

    static final int TRIALS = 2;
    static final long BASE_SECS_PER_RUN = 4;
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    static boolean includeLocks = false;

    public static void main(String[] args) throws Exception {
        if (args.length > 0)
            includeLocks = true;

        System.out.println("Warmup...");
        for (int j = 0; j < 2; ++j) {
            for (int i = 1; i <= NCPUS; ++i) {
                runCalibration(i, 1000);
                oneRun(i, loopIters[i] / 8, false);
            }
        }

        for (int i = 1; i <= NCPUS; ++i) 
            loopIters[i] = 0;

        for (int j = 0; j < TRIALS; ++j) {
            System.out.println("Trial " + j);
            for (int i = 1; i <= NCPUS; ++i) {
                runCalibration(i, BASE_SECS_PER_RUN * 1000L);
                oneRun(i, loopIters[i], true);
            }
        }
    }

    static final AtomicLong totalIters = new AtomicLong(0);
    static final AtomicLong successes = new AtomicLong(0);
    static final AtomicInteger sum = new AtomicInteger(0);

    static final LoopHelpers.MarsagliaRandom rng = new LoopHelpers.MarsagliaRandom();

    static final long[] loopIters = new long[NCPUS+1];

    static final class NonAtomicInteger {
        volatile int readBarrier;
        int value;

        NonAtomicInteger() {}
        int get() {
            int junk = readBarrier;
            return value;
        }
        boolean compareAndSet(int cmp, int val) {
            if (value == cmp) {
                value = val;
                return true;
            }
            return false;
        }
        void set(int val) { value = val; }
    }

    static final class VolatileInteger {
        volatile int value;

        VolatileInteger() {}
        int get() {
            return value;
        }
        boolean compareAndSet(int cmp, int val) {
            if (value == cmp) {
                value = val;
                return true;
            }
            return false;
        }
        void set(int val) { value = val; }
    }

    static final class SynchedInteger {
        int value;

        SynchedInteger() {}
        int get() {
            return value;
        }
        synchronized boolean compareAndSet(int cmp, int val) {
            if (value == cmp) {
                value = val;
                return true;
            }
            return false;
        }
        synchronized void set(int val) { value = val; }
    }


    static final class LockedInteger extends AbstractQueuedSynchronizer {
        int value;
        LockedInteger() {}

        public boolean tryAcquire(int acquires) {
            return compareAndSetState(0, 1);
        }
        public boolean tryRelease(int releases) {
            setState(0);
            return true;
        }
        void lock() { acquire(1); }
        void unlock() { release(1); }

        int get() {
            return value;
        }
        boolean compareAndSet(int cmp, int val) {
            lock();
            try {
                if (value == cmp) {
                    value = val;
                    return true;
                }
                return false;
            } finally {
                unlock();
            }
        }
        void set(int val) { 
            lock(); 
            try {
                value = val; 
            } finally {
                unlock();
            }
        }
    }

    // All these versions are copy-paste-hacked to avoid 
    // contamination with virtual call resolution etc.

    // Use fixed-length unrollable inner loops to reduce safepoint checks
    static final int innerPerOuter = 16;

    static final class NonAtomicLoop implements Runnable {
        final long iters;
        final NonAtomicInteger obj;
        final CyclicBarrier barrier;
        NonAtomicLoop(long iters, NonAtomicInteger obj, CyclicBarrier b) {
            this.iters = iters;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long i = iters;
                int y = 0;
                int succ = 0;
                while (i > 0) {
                    for (int k = 0; k < innerPerOuter; ++k) {
                        int x = obj.get();
                        int z = y + LoopHelpers.compute6(x);
                        if (obj.compareAndSet(x, z))
                            ++succ;
                        y = LoopHelpers.compute7(z);
                    }
                    i -= innerPerOuter;
                }
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static final class AtomicLoop implements Runnable {
        final long iters;
        final AtomicInteger obj;
        final CyclicBarrier barrier;
        AtomicLoop(long iters, AtomicInteger obj, CyclicBarrier b) {
            this.iters = iters;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long i = iters;
                int y = 0;
                int succ = 0;
                while (i > 0) {
                    for (int k = 0; k < innerPerOuter; ++k) {
                        int x = obj.get();
                        int z = y + LoopHelpers.compute6(x);
                        if (obj.compareAndSet(x, z))
                            ++succ;
                        y = LoopHelpers.compute7(z);
                    }
                    i -= innerPerOuter;
                }
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static final class VolatileLoop implements Runnable {
        final long iters;
        final VolatileInteger obj;
        final CyclicBarrier barrier;
        VolatileLoop(long iters, VolatileInteger obj, CyclicBarrier b) {
            this.iters = iters;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long i = iters;
                int y = 0;
                int succ = 0;
                while (i > 0) {
                    for (int k = 0; k < innerPerOuter; ++k) {
                        int x = obj.get();
                        int z = y + LoopHelpers.compute6(x);
                        if (obj.compareAndSet(x, z))
                            ++succ;
                        y = LoopHelpers.compute7(z);
                    }
                    i -= innerPerOuter;
                }
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static final class SynchedLoop implements Runnable {
        final long iters;
        final SynchedInteger obj;
        final CyclicBarrier barrier;
        SynchedLoop(long iters, SynchedInteger obj, CyclicBarrier b) {
            this.iters = iters;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long i = iters;
                int y = 0;
                int succ = 0;
                while (i > 0) {
                    for (int k = 0; k < innerPerOuter; ++k) {
                        int x = obj.get();
                        int z = y + LoopHelpers.compute6(x);
                        if (obj.compareAndSet(x, z))
                            ++succ;
                        y = LoopHelpers.compute7(z);
                    }
                    i -= innerPerOuter;
                }
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static final class LockedLoop implements Runnable {
        final long iters;
        final LockedInteger obj;
        final CyclicBarrier barrier;
        LockedLoop(long iters, LockedInteger obj, CyclicBarrier b) {
            this.iters = iters;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long i = iters;
                int y = 0;
                int succ = 0;
                while (i > 0) {
                    for (int k = 0; k < innerPerOuter; ++k) {
                        int x = obj.get();
                        int z = y + LoopHelpers.compute6(x);
                        if (obj.compareAndSet(x, z))
                            ++succ;
                        y = LoopHelpers.compute7(z);
                    }
                    i -= innerPerOuter;
                }
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static final int loopsPerTimeCheck = 2048;

    static final class NACalibrationLoop implements Runnable {
        final long endTime;
        final NonAtomicInteger obj;
        final CyclicBarrier barrier;
        NACalibrationLoop(long endTime, NonAtomicInteger obj, CyclicBarrier b) {
            this.endTime = endTime;
            this.obj = obj;
            this.barrier = b;
            obj.set(rng.next());
        }

        public void run() {
            try {
                barrier.await(); 
                long iters = 0;
                int y = 0;
                int succ = 0;
                do {
                    int i = loopsPerTimeCheck;
                    while (i > 0) {
                        for (int k = 0; k < innerPerOuter; ++k) {
                            int x = obj.get();
                            int z = y + LoopHelpers.compute6(x);
                            if (obj.compareAndSet(x, z))
                                ++succ;
                            y = LoopHelpers.compute7(z);
                        }
                        i -= innerPerOuter;
                    }
                    iters += loopsPerTimeCheck;
                } while (System.currentTimeMillis() < endTime);
                totalIters.getAndAdd(iters);
                sum.getAndAdd(obj.get());
                successes.getAndAdd(succ);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }

    static void runCalibration(int n, long nms) throws Exception {
        long now = System.currentTimeMillis();
        long endTime = now + nms;
        CyclicBarrier b = new CyclicBarrier(n+1);
        totalIters.set(0);
        NonAtomicInteger a = new NonAtomicInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new NACalibrationLoop(endTime, a, b)).start();
        b.await();
        b.await();
        long ipt = totalIters.get() / n;
        if (ipt > loopIters[n])
            loopIters[n] = ipt;
        if (sum.get() == 0) System.out.print(" ");
    }

    static long runNonAtomic(int n, long iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier b = new CyclicBarrier(n+1, timer);
        NonAtomicInteger a = new NonAtomicInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new NonAtomicLoop(iters, a, b)).start();
        b.await();
        b.await();
        if (sum.get() == 0) System.out.print(" ");
        return timer.getTime();
    }

    static long runAtomic(int n, long iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier b = new CyclicBarrier(n+1, timer);
        AtomicInteger a = new AtomicInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new AtomicLoop(iters, a, b)).start();
        b.await();
        b.await();
        if (sum.get() == 0) System.out.print(" ");
        return timer.getTime();
    }

    static long runVolatile(int n, long iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier b = new CyclicBarrier(n+1, timer);
        VolatileInteger a = new VolatileInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new VolatileLoop(iters, a, b)).start();
        b.await();
        b.await();
        if (sum.get() == 0) System.out.print(" ");
        return timer.getTime();
    }


    static long runSynched(int n, long iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier b = new CyclicBarrier(n+1, timer);
        SynchedInteger a = new SynchedInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new SynchedLoop(iters, a, b)).start();
        b.await();
        b.await();
        if (sum.get() == 0) System.out.print(" ");
        return timer.getTime();
    }

    static long runLocked(int n, long iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier b = new CyclicBarrier(n+1, timer);
        LockedInteger a = new LockedInteger();
        for (int j = 0; j < n; ++j) 
            new Thread(new LockedLoop(iters, a, b)).start();
        b.await();
        b.await();
        if (sum.get() == 0) System.out.print(" ");
        return timer.getTime();
    }

    static void report(String tag, long runtime, long basetime, long iters) {
        System.out.print(tag);
        System.out.print(LoopHelpers.rightJustify((runtime - basetime) / iters) + " ns");
        double secs = (double)(runtime) / 1000000000.0;
        System.out.println("\t " + secs + "s run time");
    }
        

    static void oneRun(int i, long iters, boolean print) throws Exception {
        System.out.println("threads : " + i + 
                           " base iters per thread per run : " + 
                           LoopHelpers.rightJustify(loopIters[i]));
        long ntime = runNonAtomic(i,  iters);
        if (print)
            report("Base        : ", ntime, ntime, iters);
        Thread.sleep(100L);
        long atime = runAtomic(i, iters);
        if (print)
            report("Atomic CAS  : ", atime, ntime, iters);
        Thread.sleep(100L);
        long vtime = runVolatile(i, iters);
        if (print)
            report("Volatile    : ", vtime, ntime, iters);

        Thread.sleep(100L);
        if (!includeLocks) return;
        long mtime = runLocked(i, iters);
        if (print)
            report("Mutex       : ", mtime, ntime, iters);
        Thread.sleep(100L);
        long stime = runSynched(i, iters);
        if (print) 
            report("Synchronized: ", stime, ntime, iters);
        Thread.sleep(100L);
    }


}
