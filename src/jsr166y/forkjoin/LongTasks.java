/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import static jsr166y.forkjoin.TaskTypes.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Parallel long operations on collections and arrays. This class is a
 * stand-in for functionality that will probably be supported in some
 * other way in Java 7.
 */
public class LongTasks {
    /**
     * default granularity for divide-by-two tasks. Provides about
     * four times as many finest-grained tasks as there are CPUs.
     */
    static int defaultGranularity(ForkJoinExecutor ex, int n) {
        int threads = ex.getParallelismLevel();
        return 1 + n / ((threads << 2) - 3);
    }

    /**
     * Applies the given procedure to each element of the array.
     * @param ex the executor
     * @param array the array
     * @param proc the procedure
     */
    public static <T> void apply(ForkJoinExecutor ex,
                                 long[] array, 
                                 LongProcedure proc) {
        int n = array.length;
        ex.invoke(new FJApplyer(array, proc, 0, n-1, defaultGranularity(ex, n)));
    }
    
    /**
     * Returns reduction of given array
     * @param ex the executor
     * @param array the array
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static long reduce(ForkJoinExecutor ex,
                             long[] array, 
                             LongReducer reducer,
                             long base) {
        int n = array.length;
        FJReducer r = new FJReducer(array,reducer, base,
                                    0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Applies mapper to each element of list and reduces result
     * @param ex the executor
     * @param list the list
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T> long reduce(ForkJoinExecutor ex,
                          List<T> list, 
                          MapperToLong<T> mapper,
                          LongReducer reducer,
                          long base) {
        int n = list.size();
        FJMapReducer<T> r =
            new FJMapReducer<T>(list, mapper, reducer, base,
                                0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Applies mapper to each element of array and reduces result
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static <T> long reduce(ForkJoinExecutor ex,
                          long[] array, 
                          LongTransformer mapper,
                          LongReducer reducer,
                          long base) {
        int n = array.length;
        FJTransformReducer r =
            new FJTransformReducer(array, mapper, reducer, base,
                                   0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Returns a array mapping each element of given array using mapper
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     */
    public static long[] map(ForkJoinExecutor ex,
                            long[] array, 
                            LongTransformer mapper) {
        int n = array.length;
        long[] dest = new long[n];
        ex.invoke(new FJMapper(array, dest, mapper,  
                                 0, n-1, defaultGranularity(ex, n)));
        return dest;
    }

    /**
     * Returns an element of the array matching the given predicate, or
     * missing if none
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     * @param missing the value to return if no such element exists
     */
    public static long findAny(ForkJoinExecutor ex,
                              long[] array, 
                              LongPredicate  pred,
                              long missing) {
        int n = array.length;
        VolatileLong result = new VolatileLong(missing);
        ex.invoke(new FJFindAny(array, pred, result, missing,
                                  0, n-1, defaultGranularity(ex, n)));
        return result.value;
    }

    static final class VolatileLong {
        volatile long value;
        VolatileLong(long v) { value = v; }
    }

    /**
     * Returns a list of all elements of the array matching pred
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     */
    public static List<Long> findAll(ForkJoinExecutor ex,
                                        long[] array, 
                                        LongPredicate pred) {
        int n = array.length;
        Vector<Long> dest = new Vector<Long>(); // todo: use smarter list
        ex.invoke(new FJFindAll(array, pred, dest,  
                                  0, n-1, defaultGranularity(ex, n)));
        return dest;
    }


    /**
     * Sorts the given array
     * @param ex the executor
     * @param array the array
     */
    public static void sort(ForkJoinExecutor ex, long[] array) {
        int n = array.length;
        long[] workSpace = new long[n];
        ex.invoke(new FJSorter(array, 0, workSpace, 0, n));
    }


    /**
     * Returns the sum of all elements
     * @param ex the executor
     * @param array the array
     */
    public static long sum(ForkJoinExecutor ex, 
                             long[] array) {
        int n = array.length;
        FJSum r = new FJSum(array, 0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Returns the sum of all mapped elements
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     */
    public static long sum(ForkJoinExecutor ex, 
                             long[] array, 
                             LongTransformer mapper) {
        int n = array.length;
        FJTransformSum r = 
            new FJTransformSum(array, mapper, 0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Replaces each element with running cumulative sum.
     * @param ex the executor
     * @param array the array
     * @return the sum of all elements
     */
    public static long cumulate(ForkJoinExecutor ex, long[] array) {
        int n = array.length;
        if (n == 0)
            return 0;
        if (n == 1)
            return array[0];
        int gran;
        int threads = ex.getParallelismLevel();
        if (threads == 1)
            gran = n;
        else
            gran =  n / (threads << 3);
        if (gran < 1024)
            gran = 1024;
        FJCumulator.Ctl ctl = new FJCumulator.Ctl(array, gran);
        FJCumulator r = new FJCumulator(null, ctl, 0, n);
        ex.invoke(r);
        return array[n-1];
    }


    /**
     * Returns the minimum of all elements, or MAX_VALUE if empty
     * @param ex the executor
     * @param array the array
     */
    public static long min(ForkJoinExecutor ex, 
                             long[] array) {
        int n = array.length;
        FJMin r = new FJMin(array, 0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }

    /**
     * Returns the maximum of all elements, or MIN_VALUE if empty
     * @param ex the executor
     * @param array the array
     */
    public static long max(ForkJoinExecutor ex, 
                             long[] array) {
        int n = array.length;
        FJMax r = new FJMax(array, 0, n-1, defaultGranularity(ex, n));
        ex.invoke(r);
        return r.result;
    }


    /**
     * Fork/Join version of apply
     */
    static final class FJApplyer extends RecursiveAction {
        final long[] array;
        final LongProcedure f;
        final int lo;
        final int hi;
        final int gran;
        FJApplyer next;

        FJApplyer(long[] array, LongProcedure f, int lo, int hi, int gran){
            this.array = array;
            this.f = f;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJApplyer right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJApplyer r = new FJApplyer(array, f, mid+1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                f.apply(array[i]);
            while (right != null) {
                right.join();
                right = right.next;
            }
        }
    }              

    /**
     * Fork/Join version of MapReduce
     */
    static final class FJMapReducer<T> extends RecursiveAction {
        final List<T> list;
        final MapperToLong<T> mapper;
        final LongReducer reducer;
        final long base;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJMapReducer<T> next;

        FJMapReducer(List<T> list, 
                   MapperToLong<T> mapper,
                   LongReducer reducer,
                   long base,
                   int lo, 
                   int hi, 
                   int gran) {
            this.list = list;
            this.mapper = mapper;
            this.reducer = reducer;
            this.base = base;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }


        protected void compute() {
            FJMapReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMapReducer<T> r = 
                    new FJMapReducer<T>(list, mapper, reducer, 
                                           base, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, mapper.map(list.get(i)));
            while (right != null) {
                right.join();
                x = reducer.combine(x, right.result);
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of TransformReduce
     */
    static final class FJTransformReducer extends RecursiveAction {
        final long[] array;
        final LongTransformer mapper;
        final LongReducer reducer;
        final long base;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJTransformReducer next;

        FJTransformReducer(long[] array, 
                           LongTransformer mapper,
                           LongReducer reducer,
                           long base,
                           int lo, 
                           int hi, 
                           int gran) {
            this.array = array;
            this.mapper = mapper;
            this.reducer = reducer;
            this.base = base;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJTransformReducer right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJTransformReducer r = 
                    new FJTransformReducer(array, mapper, reducer, 
                                           base, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, mapper.map(array[i]));
            while (right != null) {
                right.join();
                x = reducer.combine(x, right.result);
                right = right.next;
            }
            result = x;
        }

    }              

    /**
     * Fork/Join version of Map
     */
    static final class FJMapper extends RecursiveAction {
        final long[] array;
        final long[] dest;
        final LongTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper next;

        FJMapper(long[] array, 
                 long[] dest,
                 LongTransformer mapper,
                 int lo, 
                 int hi, 
                 int gran) {
            this.array = array;
            this.dest = dest;
            this.mapper = mapper;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }


        protected void compute() {
            FJMapper right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMapper r = 
                    new FJMapper(array, dest, mapper, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                dest[i] = mapper.map(array[i]);
            while (right != null) {
                right.join();
                right = right.next;
            }
        }
    }              

    /**
     * Fork/Join version of Reduce
     */
    static final class FJReducer extends RecursiveAction {
        final long[] array;
        final LongReducer reducer;
        final long base;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJReducer next;

        FJReducer(long[] array, 
                  LongReducer reducer,
                  long base,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.reducer = reducer;
            this.base = base;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJReducer right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJReducer r = 
                    new FJReducer(array, reducer, base, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, array[i]);
            while (right != null) {
                right.join();
                x = reducer.combine(x, right.result);
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of sum
     */
    static final class FJSum extends RecursiveAction {
        final long[] array;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJSum next;

        FJSum(long[] array, 
              int lo, 
              int hi, 
              int gran) {
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJSum right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJSum r = 
                    new FJSum(array, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = 0;
            for (int i = l; i <= h; ++i)
                x += array[i];
            while (right != null) {
                right.join();
                x += right.result;
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of min
     */
    static final class FJMin extends RecursiveAction {
        final long[] array;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJMin next;

        FJMin(long[] array, 
              int lo, 
              int hi, 
              int gran) {
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJMin right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMin r = 
                    new FJMin(array, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = Long.MAX_VALUE;
            for (int i = l; i <= h; ++i) {
                long y = array[i];
                if (y < x)
                    x = y;
            }
            while (right != null) {
                right.join();
                long y = right.result;
                if (y < x)
                    x = y;
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of max
     */
    static final class FJMax extends RecursiveAction {
        final long[] array;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJMax next;

        FJMax(long[] array, 
              int lo, 
              int hi, 
              int gran) {
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJMax right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMax r = 
                    new FJMax(array, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = Long.MAX_VALUE;
            for (int i = l; i <= h; ++i) {
                long y = array[i];
                if (y > x)
                    x = y;
            }
            while (right != null) {
                right.join();
                long y = right.result;
                if (y > x)
                    x = y;
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of TransformSum
     */
    static final class FJTransformSum extends RecursiveAction {
        final long[] array;
        final LongTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        long result;
        FJTransformSum next;

        FJTransformSum(long[] array, 
                       LongTransformer mapper,
                       int lo, 
                       int hi, 
                       int gran) {
            this.array = array;
            this.mapper = mapper;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJTransformSum right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJTransformSum r = 
                    new FJTransformSum(array, mapper, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            long x = 0;
            for (int i = l; i <= h; ++i)
                x += mapper.map(array[i]);
            while (right != null) {
                right.join();
                x += right.result;
                right = right.next;
            }
            result = x;
        }

    }              

    /**
     * Fork/Join version of scan
     *
     * A basic version of scan is straightforward.
     *  Keep dividing by two to threshold segment size, and then:
     *   Pass 1: Create tree of partial sums for each segment
     *   Pass 2: For each segment, cumulate with offset of left sibling
     * See G. Blelloch's http://www.cs.cmu.edu/~scandal/alg/scan.html
     *
     * This version improves performance within FJ framework:
     * a) It allows second pass of ready left-hand sides to proceed even 
     *    if some right-hand side first passes are still executing.
     * b) It collapses the first and second passes of segments for which
     *    incoming cumulations are ready before summing.
     * c) It skips first pass for rightmost segment (whose
     *    result is not needed for second pass).
     *
     */
    static final class FJCumulator extends AsyncAction {

        /**
         * Shared control across nodes
         */
        static final class Ctl {
            final long[] array;
            final int granularity;
            /**
             * The index of the max current consecutive
             * cumulation starting from leftmost. Initially zero.
             */
            volatile int consecutiveIndex;
            /**
             * The current consecutive cumulation
             */
            long consecutiveSum;

            Ctl(long[] array, int granularity) {
                this.array = array;
                this.granularity = granularity;
            }
        }

        final FJCumulator parent;
        final FJCumulator.Ctl ctl;
        FJCumulator left, right;
        final int lo;
        final int hi;

        /** Incoming cumulative sum */
        long in;

        /** Sum of this subtree */
        long out;
        
        /**
         * Phase/state control, updated only via transitionTo, for
         * CUMULATE, SUMMED, and FINISHED bits.
         */
        volatile int phase;

        /**
         * Phase bit. When false, segments compute only their sum.
         * When true, they cumulate array elements. CUMULATE is set at
         * root at beginning of second pass and then propagated
         * down. But it may also be set earlier in two cases when
         * cumulations are known to be ready: (1) For subtrees with
         * lo==0 (the left spine of tree) (2) Leaf nodes with
         * completed predecessors.
         */
        static final int CUMULATE = 1;

        /**
         * One bit join count. For leafs, set when summed. For
         * internal nodes, becomes true when one child is summed.
         * When second child finishes summing, it then moves up tree
         * to trigger cumulate phase.
         */
        static final int SUMMED   = 2;

        /**
         * One bit join count. For leafs, set when cumulated. For
         * internal nodes, becomes true when one child is cumulated.
         * When second child finishes cumulating, it then moves up
         * tree, excecuting finish() at the root.
         */
        static final int FINISHED = 4;

        static final AtomicIntegerFieldUpdater<FJCumulator> phaseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FJCumulator.class, "phase");

        FJCumulator(FJCumulator parent,
                    FJCumulator.Ctl ctl,
                    int lo, 
                    int hi) {
            this.parent = parent;
            this.ctl = ctl;
            this.lo = lo; 
            this.hi = hi;
        }

        public void compute() {
            if (hi - lo <= ctl.granularity) {
                int cb = establishLeafPhase();
                leafSum(cb);
                propagateLeafPhase(cb);
            }
            else
                spawnTasks();
        }
        
        /**
         * decide which leaf action to take - sum, cumulate, or both
         * @return associated bit s
         */
        int establishLeafPhase() {
            for (;;) {
                int b = phase;
                if ((b & FINISHED) != 0) // already done
                    return 0;
                int cb;
                if ((b & CUMULATE) != 0)
                    cb = FINISHED;
                else if (lo == ctl.consecutiveIndex)
                    cb = (SUMMED|FINISHED);
                else 
                    cb = SUMMED;
                if (phaseUpdater.compareAndSet(this, b, b|cb))
                    return cb;
            }
        }

        void propagateLeafPhase(int cb) {
            FJCumulator c = this;
            FJCumulator p = parent;
            for (;;) {
                if (p == null) {
                    if ((cb & FINISHED) != 0)
                        c.finish();
                    break;
                }
                int pb = p.phase;
                if ((pb & cb & FINISHED) != 0) { // both finished
                    c = p;
                    p = p.parent;
                }
                else if ((pb & cb & SUMMED) != 0) { // both summed
                    int refork = 0;
                    if ((pb & CUMULATE) == 0 && p.lo == 0)
                        refork = CUMULATE;
                    int next = pb|cb|refork;
                    if (pb == next || 
                        phaseUpdater.compareAndSet(p, pb, next)) {
                        if (refork != 0)
                            p.fork();
                        cb = SUMMED; // drop finished bit
                        c = p;
                        p = p.parent;
                    }
                }
                else if (phaseUpdater.compareAndSet(p, pb, pb|cb))
                    break;
            }
        }

        void leafSum(int cb) {
            long[] array = ctl.array;
            if (cb == SUMMED) {
                if (hi < array.length) { // skip rightmost
                    long sum = 0;
                    for (int i = lo; i < hi; ++i)
                        sum += array[i];
                    out = sum;
                }
            }
            else if (cb == FINISHED) {
                long sum = in;
                for (int i = lo; i < hi; ++i)
                    sum = array[i] += sum;
            }
            else if (cb == (SUMMED|FINISHED)) {
                long cin = ctl.consecutiveSum;
                long sum = cin;
                for (int i = lo; i < hi; ++i)
                    sum = array[i] += sum;
                out = sum - cin;
                ctl.consecutiveSum = sum;
                ctl.consecutiveIndex = hi;
            }
        }

        /**
         * Returns true if can CAS CUMULATE bit true
         */
        boolean transitionToCumulate() {
            int c;
            while (((c = phase) & CUMULATE) == 0)
                if (phaseUpdater.compareAndSet(this, c, c | CUMULATE))
                    return true;
            return false;
        }

        void spawnTasks() {
            if (left == null) {
                int mid = (lo + hi) >>> 1;
                left =  new FJCumulator(this, ctl, lo, mid);
                right = new FJCumulator(this, ctl, mid, hi);
            }

            boolean cumulate = (phase & CUMULATE) != 0;
            if (cumulate) { // push down sums
                long cin = in;
                left.in = cin;
                right.in = cin + left.out;
            }

            if (!cumulate || right.transitionToCumulate())
                right.fork();
            if (!cumulate || left.transitionToCumulate())
                left.compute();
        }

    }

    /**
     * Fork/Join version of FindAny
     */
    static final class FJFindAny extends RecursiveAction {
        final long[] array;
        final LongPredicate pred;
        final VolatileLong result;
        final long missing;
        final int lo;
        final int hi;
        final int gran;

        FJFindAny(long[] array, 
                  LongPredicate pred,
                  VolatileLong result,
                  long missing,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.pred = pred;
            this.result = result;
            this.missing = missing;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        void seqCompute() {
            for (int i = lo; i <= hi; ++i) {
                long x = array[i];
                if (pred.evaluate(x) && result.value == missing) {
                    result.value = x;
                    break;
                }
            }
        }

        protected void compute() {
            if (result.value != missing)
                return;
            if (hi - lo <= gran) {
                seqCompute();
                return;
            }
            int mid = (lo + hi) >>> 1;
            FJFindAny left = 
                new FJFindAny(array, pred, result, missing, lo, mid, gran);
            left.fork();
            FJFindAny right = 
                new FJFindAny(array, pred, result, missing, mid + 1, hi, gran);
            right.invoke();
            if (result.value != missing)
                left.cancel();
            else
                left.join();
        }
    }              

    /**
     * Fork/Join version of FindAll
     */
    static final class FJFindAll extends RecursiveAction {
        final long[] array;
        final LongPredicate pred;
        final List<Long> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAll(long[] array, 
                  LongPredicate pred,
                  List<Long> result,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.pred = pred;
            this.result = result;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }


        void seqCompute() {
            for (int i = lo; i <= hi; ++i) {
                long x = array[i];
                if (pred.evaluate(x))
                    result.add(x);
            }
        }

        protected void compute() {
            if (hi - lo <= gran) {
                seqCompute();
                return;
            }
            int mid = (lo + hi) >>> 1;
            FJFindAll left = 
                new FJFindAll(array, pred, result, lo, mid, gran);
            FJFindAll right = 
                new FJFindAll(array, pred, result, mid + 1, hi, gran);
            coInvoke(left, right);
        }
    }              

    /*
     * Sort algorithm based mainly on CilkSort
     * <A href="http://supertech.lcs.mit.edu/cilk/"> Cilk</A>:
     * if array size is small, just use a sequential quicksort
     *         Otherwise:
     *         1. Break array in half.
     *         2. For each half, 
     *             a. break the half in half (i.e., quarters),
     *             b. sort the quarters
     *             c. merge them together
     *         3. merge together the two halves.
     *
     * One reason for splitting in quarters is that this guarantees
     * that the final sort is in the main array, not the workspace array.
     * (workspace and main swap roles on each subsort step.)
     * 
     */

    // Cutoff for when to do sequential versus parallel sorts and merges 
    static final int SEQUENTIAL_THRESHOLD = 256; // == 16 * 16
    // Todo: check for #cpu sensitivity


    static class FJSorter extends RecursiveAction {
        final long[] a;     // Array to be sorted.
        final int ao;    // origin of the part of array we deal with
        final long[] w;     // workspace array for merge
        final int wo;    // its origin
        final int n;     // Number of elements in (sub)arrays.

        FJSorter (long[] a, int ao, long[] w, int wo, int n) {
            this.a = a; this.ao = ao; this.w = w; this.wo = wo; this.n = n;
        }

        protected void compute()  {
            if (n <= SEQUENTIAL_THRESHOLD)
                quickSort(a, ao, ao+n-1);
            else {
                int q = n >>> 2; // lower quarter index
                int h = n >>> 1; // half
                int u = h + q;   // upper quarter

                coInvoke(new SubSorter(new FJSorter(a, ao,   w, wo,   q),
                                          new FJSorter(a, ao+q, w, wo+q, q),
                                          new FJMerger(a, ao,   q, ao+q, q, 
                                                        w, wo)),
                         new SubSorter(new FJSorter(a, ao+h, w, wo+h, q),
                                          new FJSorter(a, ao+u, w, wo+u, n-u),
                                          new FJMerger(a, ao+h, q, ao+u, n-u, 
                                                        w, wo+h)));
                new FJMerger(w, wo, h, wo+h, n-h, a, ao).compute();
            }
        }

    }

    /** 
     * A boring class to run two given sorts in parallel, then merge them.
     */
    static class SubSorter extends RecursiveAction {
        final FJSorter left;
        final FJSorter right;
        final FJMerger merger;
        SubSorter(FJSorter left, FJSorter right, FJMerger merger) {
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            coInvoke(left, right);
            merger.invoke();
        }
    }

    static class FJMerger extends RecursiveAction {
        final long[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final long[] w;      // Output array.
        final int wo;

        FJMerger (long[] a, int lo, int ln, int ro, int rn, long[] w, int wo) {
            this.a = a;
            this.w = w;
            this.wo = wo;
            // Left side should be largest of the two for fiding split.
            // Swap now, since left/right doesn't otherwise matter
            if (ln >= rn) {
                this.lo = lo;    this.ln = ln;
                this.ro = ro;    this.rn = rn;
            }
            else {
                this.lo = ro;    this.ln = rn;
                this.ro = lo;    this.rn = ln;
            }
        }

        protected void compute() {
            /*
              If partiions are small, then just sequentially merge.
              Otherwise:
              1. Split Left partition in half.
              2. Find the greatest point in Right partition
                 less than the beginning of the second half of left, 
                 via binary search.
              3. In parallel:
                  merge left half of  L with elements of R up to split point
                  merge right half of L with elements of R past split point
            */

            if (ln <= SEQUENTIAL_THRESHOLD)
                merge();
            else {
                int lh = ln >>> 1; 
                int ls = lo + lh;   // index of split 
                long split = a[ls];
                int rl = 0;
                int rh = rn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (split <= a[ro + mid])
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                coInvoke(new FJMerger(a, lo, lh,    ro,    rh,    w, wo), 
                         new FJMerger(a, ls, ln-lh, ro+rh, rn-rh, w, wo+lh+rh));
            }
        }

        /** a standard sequential merge */
        void merge() {
            int l = lo;
            int lFence = lo+ln;
            int r = ro;
            int rFence = ro+rn;
            int k = wo;
            while (l < lFence && r < rFence)
                w[k++] = (a[l] <= a[r])? a[l++] : a[r++];
            while (l < lFence) 
                w[k++] = a[l++];
            while (r < rFence) 
                w[k++] = a[r++];
        }
    }

    // Cutoff for when to use insertion-sort instead of quicksort
    static final int INSERTION_SORT_THRESHOLD = 16;

    /** A standard sequential quicksort */
    static void quickSort(long[] a, int lo, int hi) {
        // If under threshold, use insertion sort
        if (hi - lo <= INSERTION_SORT_THRESHOLD) {
            for (int i = lo + 1; i <= hi; i++) {
                long t = a[i];
                int j = i - 1;
                while (j >= lo && t < a[j]) {
                    a[j+1] = a[j];
                    --j;
                }
                a[j+1] = t;
            }
            return;
        }
        
        //  Use median-of-three(lo, mid, hi) to pick a partition. 
        //  Also swap them into relative order while we are at it.
        int mid = (lo + hi) >>> 1;
        if (a[lo] > a[mid]) {
            long t = a[lo]; a[lo] = a[mid]; a[mid] = t;
        }
        if (a[mid] > a[hi]) {
            long t = a[mid]; a[mid] = a[hi]; a[hi] = t;
            if (a[lo] > a[mid]) {
                t = a[lo]; a[lo] = a[mid]; a[mid] = t;
            }
        }
        
        long pivot = a[mid];
        int left = lo+1; 
        int right = hi-1;
        for (;;) {
            while (pivot < a[right]) 
                --right;
            while (left < right && pivot >= a[left]) 
                ++left;
            if (left < right) {
                long t = a[left]; a[left] = a[right]; a[right] = t;
                --right;
            }
            else break;
        }
        quickSort(a, lo,    left);
        quickSort(a, left+1, hi);
    }



}
