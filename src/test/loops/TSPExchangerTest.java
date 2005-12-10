/*
 * Written by Doug Lea and Bill Scherer with assistance from members
 * of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * A parallel Traveling Salesperson Problem (TSP) program based on a
 * genetic algorithm using an Exchanger.  A population of chromosomes
 * is distributed among "pools".  The chromosomes represent tours, and
 * their fitness is the total tour length. Each chromosome is
 * initialized as a random tour.  A Task is associated with each pool.
 * Each task repeatedly does, for a fixed number of iterations
 * (generations):
 *
 * <ol>
 *   <li> Select a breeder b from the pool
 *   <li> Create a strand of its tour with a random starting point and length
 *   <li> Offer the strand to the exchanger, receiving a strand from 
 *        another pool
 *   <li> Combine b and the received strand using crossing function to 
 *        create new chromosome c.
 *   <li> Replace a chromosome in the pool with c.
 * </ol>
 *
 * See below for more details.
 * <p>
 * 
 */
public class TSPExchangerTest {
    static final int DEFAULT_MAX_THREADS  =
        (Runtime.getRuntime().availableProcessors() + 2);

    /**
     * The problem size. Each city is a random point. The goal is to
     * find a tour among them with smallest total Euclidean distance.
     */
    static final int DEFAULT_CITIES = 144;

    // Tuning parameters. 

    /**
     * The number of chromosomes per pool. Must be a power of two.
     *
     * Smaller values lead to faster iterations but poorer quality
     * results
     */
    static final int DEFAULT_POOL_SIZE = 32; 

    /**
     * The number of iterations per task. Convergence appears
     * to be roughly proportional to #cities-squared
     */
    static final int DEFAULT_GENERATIONS = DEFAULT_CITIES * DEFAULT_CITIES;

    /**
     * The number of pools. The total population is #pools * poolSize,
     * which should be roughly on the order of #cities-squared
     *
     * Smaller values lead to faster total runs but poorer quality
     * results
     */
    static final int DEFAULT_NPOOLS = DEFAULT_GENERATIONS / DEFAULT_POOL_SIZE;

    /**
     * The minimum length for a random chromosome strand.
     * Must be at least 1.
     */
    static final int MIN_STRAND_LENGTH = 3;

    /**
     * The probablility mask value for creating random strands,
     * that have lengths at least MIN_STRAND_LENGTH, and grow
     * with exposnential decay 2^(-(1/(RANDOM_STRAND_MASK + 1)
     * Must be 1 less than a power of two.
     */
    static final int RANDOM_STRAND_MASK = 7;

    /**
     * Probablility control for selecting breeders.
     * Breeders are selected starting at the best-fitness chromosome,
     * with exponentially decaying probablility
     * 1 / (poolSize >>> BREEDER_DECAY). 
     *
     * Larger values usually cause faster convergence but poorer
     * quality results
     */
    static final int BREEDER_DECAY = 1;

    /**
     * Probablility control for selecting dyers.
     * Dyers are selected starting at the worst-fitness chromosome,
     * with exponentially decaying probablility
     * 1 / (poolSize >>> DYER_DECAY)
     *
     * Larger values usually cause faster convergence but poorer
     * quality results
     */
    static final int DYER_DECAY = 1;

    /**
     * The probability mask for a task to give up running and
     * resubmit itself. On each iteration, a task stops iterating
     * and resubmits itself with probability 1 / (RESUBMIT_MASK+1).
     * This avoids some tasks running to completion before others
     * even start when there are more pools than threads.
     *
     * Must be 1 less than a power of two.
     */
    static final int RESUBMIT_MASK = 63;

    static final boolean verbose = true;
    static final long SNAPSHOT_RATE = 10000; // in milliseconds

    /**
     * The set of cities. Created once per program run, to
     * make it easier to compare solutions across different runs.
     */
    static CitySet cities; 

    public static void main(String[] args) throws Exception {
        int maxThreads = DEFAULT_MAX_THREADS;
        int nCities = DEFAULT_CITIES;
        int poolSize = DEFAULT_POOL_SIZE;
        int nGen = nCities * nCities;
        int nPools = nCities * nCities / poolSize;

        try {
            int argc = 0;
            while (argc < args.length) {
                String option = args[argc++];
                if (option.equals("-c")) {
                    nCities = Integer.parseInt(args[argc]);
                    nGen = nCities * nCities;
                    nPools = nCities * nCities / poolSize;
                }
                else if (option.equals("-p"))
                    poolSize = Integer.parseInt(args[argc]);
                else if (option.equals("-g"))
                    nGen = Integer.parseInt(args[argc]);
                else if (option.equals("-n"))
                    nPools = Integer.parseInt(args[argc]);
                else
                    maxThreads = Integer.parseInt(option);
                argc++;
            }
        }
        catch (Exception e) {
            reportUsageErrorAndDie();
        }

        System.out.print("TSPExchangerTest");
        System.out.print(" -c " + nCities);
        System.out.print(" -g " + nGen);
        System.out.print(" -p " + poolSize);
        System.out.print(" -n " + nPools);
        System.out.print(" max threads " + maxThreads);
        System.out.println();

        cities = new CitySet(nCities);

        for (int i = 2; i <= maxThreads; i += 2) 
            oneRun(i, nPools, poolSize, nGen);
    }

    static void reportUsageErrorAndDie() {
        System.out.print("usage: TSPExchangerTest");
        System.out.print(" [-c #cities]");
        System.out.print(" [-p #poolSize]");
        System.out.print(" [-g #generations]");
        System.out.print(" [-n #pools]");
        System.out.print(" #threads]");
        System.out.println();
        System.exit(0);
    }

    /**
     * Perform one run with the given parameters.  Each run completes
     * when all but one of the tasks has finished.  The last remaining
     * task may have no one left to exchange with, so the pool is
     * abruptly terminated.
     */
    static void oneRun(int nThreads, int nPools, int poolSize, int nGen) 
        throws InterruptedException {
        Population p = new Population(nThreads, nPools, poolSize, nGen);
        ProgressMonitor mon = null;
        if (verbose) {
            mon = new ProgressMonitor(p);
            mon.start();
        }
        p.printSnapshot(0);
        long startTime = System.nanoTime();
        p.start();
        p.awaitTasks();
        long stopTime = System.nanoTime();
        if (mon != null)
            mon.interrupt();
        p.shutdown();
        Thread.sleep(100);

        long elapsed = stopTime - startTime;
        long rate = elapsed / (nPools * nGen);
        double secs = (double)elapsed / 1000000000.0;
        p.printSnapshot(secs);
        System.out.printf("%10d ns per transfer\n", rate);
    }


    /**
     * A Population creates the pools, tasks, and threads for a run
     * and has control methods to start, stop, and report progress.
     */
    static final class Population {
        final Task[] tasks;
        final Exchanger<Strand> exchanger;
        final ThreadPoolExecutor exec;
        final CountDownLatch done;
        final int nGen;
        final int poolSize;
        final int nThreads;

        Population(int nThreads, int nPools, int poolSize, int nGen) {
            this.nThreads = nThreads;
            this.nGen = nGen;
            this.poolSize = poolSize;
            this.exchanger = new Exchanger<Strand>();
            this.done = new CountDownLatch(nPools-1);
            this.tasks = new Task[nPools];
            for (int i = 0; i < nPools; i++) 
                tasks[i] = new Task(this);
            BlockingQueue<Runnable> tq = 
                new LinkedBlockingQueue<Runnable>();
            this.exec = new ThreadPoolExecutor(nThreads, nThreads,
                                               0L, TimeUnit.MILLISECONDS,
                                               tq);
            exec.prestartAllCoreThreads();
        }

        /** Start the tasks */
        void start() {
            for (int i = 0; i < tasks.length; i++) 
                exec.execute(tasks[i]);
        }

        /** Stop the tasks */
        void shutdown() {
            exec.shutdownNow();
        }

        /** Called by task upon terminations */
        void taskDone() {
            done.countDown();
        }

        /** Wait for (all but one) task to complete */
        void awaitTasks() throws InterruptedException {
            done.await();
        }

        /** 
         * Called by a task to resubmit itself after completing
         * fewer than nGen iterations.
         */
        void resubmit(Task task) {
            exec.execute(task);
        }

        void printSnapshot(double secs) {
            int gens = 0;
            double best = Double.MAX_VALUE;
            double worst = 0;
            for (int k = 0; k < tasks.length; ++k) {
                gens += tasks[k].gen;
                Chromosome[] cs = tasks[k].chromosomes;
                float b = cs[0].fitness;
                if (b < best)
                    best = b;
                float w = cs[cs.length-1].fitness;
                if (w > worst)
                    worst = w;
            }
            int avegen = (done.getCount() == 0)? nGen : gens / tasks.length;
            System.out.printf("Time:%9.3f Best:%9.3f Worst:%9.3f Gen:%6d Threads:%4d\n",
                              secs, best, worst, avegen, nThreads);
        }
        
        float averageFitness() { // currently unused
            float total = 0;
            int count = 0;
            for (int k = 0; k < tasks.length; ++k) {
                Chromosome[] cs = tasks[k].chromosomes;
                for (int i = 0; i < cs.length; i++)
                    total += cs[i].fitness;
                count += cs.length;
            }
            return total/(float)count;
        }
    }

    /**
     * A Task updates its pool of chromosomes.. 
     */
    static final class Task implements Runnable {
        /** The pool of chromosomes, kept in sorted order */
        final Chromosome[] chromosomes;
        final Population pop;
        /** The common exchanger, same for all tasks */
        final Exchanger<Strand> exchanger;
        /** The current strand being exchanged */
        Strand strand;
        /** Bitset used in cross */
        final int[] inTour;
        final RNG rng;
        final int poolSize;
        final int nGen;
        int gen;

        Task(Population pop) {
            this.pop = pop;
            this.nGen = pop.nGen;
            this.gen = 0;
            this.poolSize = pop.poolSize;
            this.exchanger = pop.exchanger;
            this.rng = new RNG();
            int length = cities.length;
            this.strand = new Strand(length);
            this.inTour = new int[(length >>> 5) + 1];
            this.chromosomes = new Chromosome[poolSize];
            for (int j = 0; j < poolSize; ++j)
                chromosomes[j] = new Chromosome(length, rng);
            Arrays.sort(chromosomes);
        }

        /**
         * Run one or more update cycles. 
         */
        public void run() {
            try {
                for (;;) {
                    update();
                    if (++gen >= nGen) {
                        pop.taskDone();
                        return;
                    }
                    if ((rng.next() & RESUBMIT_MASK) == 1) {
                        pop.resubmit(this);
                        return;
                    } 
                }
            } catch (InterruptedException ie) {
                pop.taskDone();
            }
        }

        /**
         * Choose a breeder, exchange strand with another pool, and
         * cross them to create new chromosome to replace a chosen
         * dyer.
         */
        void update() throws InterruptedException {
            int b = chooseBreeder();
            int d = chooseDyer(b);
            Chromosome breeder = chromosomes[b];
            Chromosome child = chromosomes[d];
            chooseStrand(breeder);
            strand = exchanger.exchange(strand);
            cross(breeder, child);
            fixOrder(child, d);
        }

        /**
         * Choose a breeder, with exponentially decreasing probability
         * starting at best.
         * @return index of selected breeder
         */
        int chooseBreeder() {
            int mask = (poolSize >>> BREEDER_DECAY) - 1;
            int b = 0;
            while ((rng.next() & mask) != mask) {
                if (++b >= poolSize)
                    b = 0;
            }
            return b;
        }

        /**
         * Choose a chromosome that will be replaced, with
         * exponentially decreasing probablility starting at
         * worst, ignoring the excluded index
         * @param exclude index to ignore; use -1 to not exclude any
         * @return index of selected dyer
         */
        int chooseDyer(int exclude) {
            int mask = (poolSize >>> DYER_DECAY)  - 1;
            int d = poolSize - 1;
            while (d == exclude || (rng.next() & mask) != mask) {
                if (--d < 0)
                    d = poolSize - 1;
            }
            return d;
        }

        /** 
         * Select a random strand of b's.
         * @param breeder the breeder
         */
        void chooseStrand(Chromosome breeder) {
            int[] bs = breeder.alleles;
            int length = bs.length;
            int strandLength = MIN_STRAND_LENGTH;
            while (strandLength < length &&
                   (rng.next() & RANDOM_STRAND_MASK) != RANDOM_STRAND_MASK)
                strandLength++;
            strand.strandLength = strandLength;
            int[] ss = strand.alleles;
            int k = (rng.next() & 0x7FFFFFFF) % length;
            for (int i = 0; i < strandLength; ++i) {
                ss[i] = bs[k];
                if (++k >= length) k = 0;
            }
        }

        /**
         * Copy current strand to start of c's, and then append all
         * remaining b's that aren't in the strand.
         * @param breeder the breeder
         * @param child the child
         */
        void cross(Chromosome breeder, Chromosome child) {
            for (int k = 0; k < inTour.length; ++k) // clear bitset
                inTour[k] = 0;

            // Copy current strand to c
            int[] cs = child.alleles;
            int ssize = strand.strandLength;
            int[] ss = strand.alleles;
            int i;
            for (i = 0; i < ssize; ++i) {
                int x = ss[i];
                cs[i] = x;
                inTour[x >>> 5] |= 1 << (x & 31); // record in bit set
            }

            // Find index of matching origin in b
            int first = cs[0];
            int j = 0;
            int[] bs = breeder.alleles;
            while (bs[j] != first) 
                ++j;

            // Append remaining b's that aren't already in tour
            while (i < cs.length) {
                if (++j >= bs.length) j = 0;
                int x = bs[j];
                if ((inTour[x >>> 5] & (1 << (x & 31))) == 0)
                    cs[i++] = x;
            } 

        }

        /**
         * Fix the sort order of a changed Chromosome c at position k
         * @param c the chromosome
         * @param k the index 
         */
        void fixOrder(Chromosome c, int k) {
            Chromosome[] cs = chromosomes;
            float oldFitness = c.fitness;
            c.recalcFitness();
            float newFitness = c.fitness;
            if (newFitness < oldFitness) {
                int j = k;
                int p = j - 1;
                while (p >= 0 && cs[p].fitness > newFitness) {
                    cs[j] = cs[p];
                    j = p--;
                }
                cs[j] = c;
            } else if (newFitness > oldFitness) {
                int j = k;
                int n = j + 1;
                while (n < cs.length && cs[n].fitness < newFitness) {
                    cs[j] = cs[n];
                    j = n++;
                }
                cs[j] = c;
            }
        }
    }

    /**
     * A Chromosome is a candidate TSP tour.
     */
    static final class Chromosome implements Comparable {
        /** Index of cities in tour order */
        final int[] alleles;
        /** Total tour length */
        float fitness;

        /** 
         * Initialize to random tour
         */
        Chromosome(int length, RNG random) {
            alleles = new int[length];
            for (int i = 0; i < length; i++)
                alleles[i] = i;
            for (int i = length - 1; i > 0; i--) {
                int idx = (random.next() & 0x7FFFFFFF) % alleles.length;
                int tmp = alleles[i];
                alleles[i] = alleles[idx];
                alleles[idx] = tmp;
            }
            recalcFitness();
        }

        public int compareTo(Object x) { // to enable sorting
            float xf = ((Chromosome)x).fitness;
            float f = fitness;
            return ((f == xf)? 0 :((f < xf)? -1 : 1));
        }

        void recalcFitness() {
            int[] a = alleles;
            int len = a.length;
            int p = a[0];
            float f = cities.distanceBetween(a[len-1], p);
            for (int i = 1; i < len; i++) {
                int n = a[i];
                f += cities.distanceBetween(p, n);
                p = n;
            }
            fitness = f;
        }

        void validate() { // Ensure that this is a valid tour.
            int len = alleles.length;
            boolean[] used = new boolean[len];
            for (int i = 0; i < len; ++i) 
                used[alleles[i]] = true;
            for (int i = 0; i < len; ++i) 
                if (!used[i])
                    throw new Error("Bad tour");
        }

    }

    /**
     * A Strand is a random sub-sequence of a Chromosome.  Each task
     * creates only one strand, and then trades it with others,
     * refilling it on each iteration.
     */
    static final class Strand {
        final int[] alleles;
        int strandLength;
        Strand(int length) { alleles = new int[length]; }
    }

    /**
     * A collection of (x,y) points that represent cities.  Distances
     * are scaled in [0,1) to simply checking results.  The expected
     * optimal TSP for random points is believed to be around 0.76 *
     * sqrt(N). For papers discussing this, see
     * http://www.densis.fee.unicamp.br/~moscato/TSPBIB_home.html for
     */
    static final class CitySet {
        // Scale ints to doubles in [0,1)
        static final double PSCALE = (double)0x80000000L;

        final int length;
        final int[] xPts;
        final int[] yPts;
        final float[][] distances;

        CitySet(int n) {
            this.length = n;
            this.xPts = new int[n];
            this.yPts = new int[n];
            this.distances = new float[n][n];

            RNG random = new RNG();
            for (int i = 0; i < n; i++) {
                xPts[i] = (random.next() & 0x7FFFFFFF);
                yPts[i] = (random.next() & 0x7FFFFFFF);
            }

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double dX = (xPts[i] - xPts[j]) / PSCALE;
                    double dY = (yPts[i] - yPts[j]) / PSCALE;
                    distances[i][j] = (float)Math.hypot(dX, dY);
                }
            }
        }

        // Retrieve the cached distance between a pair of cities
        float distanceBetween(int i, int j) {
            return distances[i][j];
        }
    }

    /**
     * Cheap XorShift random number generator
     */
    static final class RNG {
        /** Seed generator for XorShift RNGs */
        static final Random seedGenerator = new Random();

        int seed;
        RNG(int seed) { this.seed = seed; }
        RNG()         { this.seed = seedGenerator.nextInt(); }

        int next() {
            int x = seed;
            x ^= x << 6;
            x ^= x >>> 21;
            x ^= x << 7;
            seed = x;
            return x;
        }
    }

    static final class ProgressMonitor extends Thread {
        final Population pop;
        ProgressMonitor(Population p) { pop = p; }
        public void run() {
            double time = 0;
            try {
                while (!Thread.interrupted()) {
                    sleep(SNAPSHOT_RATE);
                    time += SNAPSHOT_RATE;
                    pop.printSnapshot(time / 1000.0);
                }
            } catch (InterruptedException ie) {}
        }
    }
}
