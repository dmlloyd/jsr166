/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

/*
 * @test
 * @bug 4486658
 * @compile -source 1.5 MultipleProducersSingleConsumerLoops.java
 * @run main/timeout=3600 MultipleProducersSingleConsumerLoops
 * @summary  multiple producers and single consumer using blocking queues
 */

import java.util.concurrent.*;

public class MultipleProducersSingleConsumerLoops {
    static final int CAPACITY =      100;
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;
    static int producerSum;
    static int consumerSum;

    static synchronized void addProducerSum(int x) {
        producerSum += x;
    }

    static synchronized void addConsumerSum(int x) {
        consumerSum += x;
    }

    static synchronized void checkSum() {
        if (producerSum != consumerSum)
            throw new Error("CheckSum mismatch");
    }

    public static void main(String[] args) throws Exception {
        int maxProducers = 5;
        int iters = 100000;

        if (args.length > 0)
            maxProducers = Integer.parseInt(args[0]);

        print = false;
        System.out.println("Warmup...");
        oneTest(1, 10000);
        Thread.sleep(100);
        oneTest(2, 10000);
        Thread.sleep(100);
        print = true;

        for (int i = 1; i <= maxProducers; i += (i+1) >>> 1) {
            System.out.println("----------------------------------------");
            System.out.println("Producers:" + i);
            oneTest(i, iters);
            Thread.sleep(100);
        }
        pool.shutdown();
        if (! pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            throw new Error();
   }

    static void oneTest(int producers, int iters) throws Exception {
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY), producers, iters);
        oneRun(new LinkedBlockingQueue<Integer>(CAPACITY), producers, iters);
        oneRun(new LinkedBlockingDeque<Integer>(CAPACITY), producers, iters);
        oneRun(new LinkedTransferQueue<Integer>(), producers, iters);

        // Don't run PBQ since can legitimately run out of memory
        //        if (print)
        //            System.out.print("PriorityBlockingQueue   ");
        //        oneRun(new PriorityBlockingQueue<Integer>(), producers, iters);

        oneRun(new SynchronousQueue<Integer>(), producers, iters);
        if (print)
            System.out.println("fair implementations:");
        oneRun(new SynchronousQueue<Integer>(true), producers, iters);
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY, true), producers, iters);
    }

    static abstract class Stage implements Runnable {
        final int iters;
        final BlockingQueue<Integer> queue;
        final CyclicBarrier barrier;
        Stage(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            queue = q;
            barrier = b;
            this.iters = iters;
        }
    }

    static class Producer extends Stage {
        Producer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);
        }

        public void run() {
            try {
                barrier.await();
                int s = 0;
                int l = hashCode();
                for (int i = 0; i < iters; ++i) {
                    l = LoopHelpers.compute1(l);
                    l = LoopHelpers.compute2(l);
                    queue.put(new Integer(l));
                    s += l;
                }
                addProducerSum(s);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }
    }

    static class Consumer extends Stage {
        Consumer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);
        }

        public void run() {
            try {
                barrier.await();
                int s = 0;
                for (int i = 0; i < iters; ++i) {
                    s += queue.take().intValue();
                }
                addConsumerSum(s);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int nproducers, int iters) throws Exception {
        if (print)
            System.out.printf("%-18s", q.getClass().getSimpleName());
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(nproducers + 2, timer);
        for (int i = 0; i < nproducers; ++i) {
            pool.execute(new Producer(q, barrier, iters));
        }
        pool.execute(new Consumer(q, barrier, iters * nproducers));
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        checkSum();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * nproducers)) + " ns per transfer");
    }

}
