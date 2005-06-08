/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CachedThreadPoolLoops {
    static final AtomicInteger remaining = new AtomicInteger();
    static final int maxIters = 1000000;

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;

        if (args.length > 0) 
            maxThreads = Integer.parseInt(args[0]);
        
        int k = 1;
        for (int i = 1; i <= maxThreads;) {
            System.out.println("Threads:" + i);
            oneTest(i);
            Thread.sleep(100);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            } 
            else 
                i = k;
        }
   }

    static void oneTest(int nThreads) throws Exception {
        System.out.print("SynchronousQueue        ");
        oneRun(new SynchronousQueue<Runnable>(), nThreads);
        System.out.print("SynchronousQueue(fair)  ");
        oneRun(new SynchronousQueue<Runnable>(true), nThreads);
    }

    static final class Task implements Runnable {
        final ThreadPoolExecutor pool;
        final CountDownLatch done;
        Task(ThreadPoolExecutor p, CountDownLatch d) { 
            pool = p; 
            done = d;
        }
        public void run() {
            done.countDown();
            remaining.incrementAndGet();
            int n;
            while (!Thread.interrupted() &&
                   (n = remaining.get()) > 0 && 
                   done.getCount() > 0) {
                if (remaining.compareAndSet(n, n-1)) {
                    try {
                        pool.execute(this);
                    }
                    catch (RuntimeException ex) {
                        System.out.print("*");
                        while (done.getCount() > 0) done.countDown();
                        return;
                    }
                }
            }
        }
    }
    
    static void oneRun(BlockingQueue<Runnable> q, int nThreads) throws Exception {
       
        ThreadPoolExecutor pool = 
            new ThreadPoolExecutor(nThreads+1, Integer.MAX_VALUE,
                                   1L, TimeUnit.SECONDS, q);

        CountDownLatch done = new CountDownLatch(maxIters);
        remaining.set(nThreads-1);
        pool.prestartAllCoreThreads();
        Task t = new Task(pool, done);
        long start = System.nanoTime();
        pool.execute(t);
        done.await();
        long time = System.nanoTime() - start;
        System.out.println("\t: " + LoopHelpers.rightJustify(time / maxIters) + " ns per task");
        pool.shutdownNow();
    }

}