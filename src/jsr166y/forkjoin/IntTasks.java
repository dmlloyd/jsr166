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
 * Parallel int operations on collections and arrays.
 */
public class IntTasks {
    /**
     * default granularity for divide-by-two tasks. Provides about
     * four times as many finest-grained tasks as there are CPUs.
     */
    static int defaultGranularity(ForkJoinPool pool, int n) {
        int threads = pool.getPoolSize();
        return 1 + n / ((threads << 2) - 3);
    }

    /**
     * Applies the given procedure to each element of the array.
     * @param pool the pool
     * @param array the array
     * @param proc the procedure
     */
    public static <T> void apply(ForkJoinPool pool,
                                 int[] array, 
                                 IntProcedure proc) {
        int n = array.length;
        pool.invoke(new FJApplyer(array, proc, 0, n, defaultGranularity(pool, n)));
    }
    
    /**
     * Returns reduction of given array
     * @param pool the pool
     * @param array the array
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static int reduce(ForkJoinPool pool,
                             int[] array, 
                             IntReducer reducer,
                             int base) {
        int n = array.length;
        FJReducer r = new FJReducer(array,reducer, base,
                                    0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Applies mapper to each element of list and reduces result
     * @param pool the pool
     * @param list the list
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T> int reduce(ForkJoinPool pool,
                                 List<T> list, 
                                 MapperToInt<T> mapper,
                                 IntReducer reducer,
                                 int base) {
        int n = list.size();
        FJMapReducer<T> r =
            new FJMapReducer<T>(list, mapper, reducer, base,
                                0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Applies mapper to each element of list and reduces result
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T> int reduce(ForkJoinPool pool,
                                 T[] array, 
                                 MapperToInt<T> mapper,
                                 IntReducer reducer,
                                 int base) {
        int n = array.length;
        FJArrayMapReducer<T> r =
            new FJArrayMapReducer<T>(array, mapper, reducer, base,
                                     0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Applies mapper to each element of array and reduces result
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static <T> int reduce(ForkJoinPool pool,
                                 int[] array, 
                                 IntTransformer mapper,
                                 IntReducer reducer,
                                 int base) {
        int n = array.length;
        FJTransformReducer r =
            new FJTransformReducer(array, mapper, reducer, base,
                                   0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns a array mapping each element of given array using mapper
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     */
    public static int[] map(ForkJoinPool pool,
                            int[] array, 
                            IntTransformer mapper) {
        int n = array.length;
        int[] dest = new int[n];
        pool.invoke(new FJMapper(array, dest, mapper,  
                                 0, n, defaultGranularity(pool, n)));
        return dest;
    }

    /**
     * Returns an element of the array matching the given predicate, or
     * missing if none
     * @param pool the pool
     * @param array the array
     * @param pred the predicate
     * @param missing the value to return if no such element exists
     */
    public static int findAny(ForkJoinPool pool,
                              int[] array, 
                              IntPredicate  pred,
                              int missing) {
        int n = array.length;
        VolatileInt result = new VolatileInt(missing);
        pool.invoke(new FJFindAny(array, pred, result, missing,
                                  0, n, defaultGranularity(pool, n)));
        return result.value;
    }

    static final class VolatileInt {
        volatile int value;
        VolatileInt(int v) { value = v; }
    }

    /**
     * Returns a list of all elements of the array matching pred
     * @param pool the pool
     * @param array the array
     * @param pred the predicate
     */
    public static List<Integer> findAll(ForkJoinPool pool,
                                        int[] array, 
                                        IntPredicate pred) {
        int n = array.length;
        Vector<Integer> dest = new Vector<Integer>(); // todo: use smarter list
        pool.invoke(new FJFindAll(array, pred, dest,  
                                  0, n, defaultGranularity(pool, n)));
        return dest;
    }


    /**
     * Sorts the given array
     * @param pool the pool
     * @param array the array
     */
    public static void sort(ForkJoinPool pool, int[] array) {
        int n = array.length;
        int[] workSpace = new int[n];
        pool.invoke(new FJSorter(array, 0, workSpace, 0, n));
    }

    /**
     * Returns the sum of all elements
     * @param pool the pool
     * @param array the array
     */
    public static int sum(ForkJoinPool pool, 
                          int[] array) {
        int n = array.length;
        FJSum r = new FJSum(array, 0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns the sum of all mapped elements
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     */
    public static int sum(ForkJoinPool pool, 
                          int[] array, 
                          IntTransformer mapper) {
        int n = array.length;
        FJTransformSum r = 
            new FJTransformSum(array, mapper, 0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Replaces each element with running cumulative sum.
     * @param pool the pool
     * @param array the array
     * @return the sum of all elements
     */
    public static int plusScan(ForkJoinPool pool, int[] array) {
        int n = array.length;
        if (n == 0)
            return 0;
        if (n == 1)
            return array[0];
        int threads = pool.getPoolSize();
        int gran = 1 + n / ((threads << 3) - 7);
        if (gran < 2048)
            gran = 2048;
        FJPlusScan r = new FJPlusScan(null, null, array, 0, n, gran);
        pool.invoke(r);
        return array[n-1];
    }

    /**
     * Returns the minimum of all elements, or MAX_VALUE if empty
     * @param pool the pool
     * @param array the array
     */
    public static int min(ForkJoinPool pool, 
                          int[] array) {
        int n = array.length;
        FJMin r = new FJMin(array, 0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns the maximum of all elements, or MIN_VALUE if empty
     * @param pool the pool
     * @param array the array
     */
    public static int max(ForkJoinPool pool, 
                          int[] array) {
        int n = array.length;
        FJMax r = new FJMax(array, 0, n, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }


    /**
     * Fork/Join version of apply
     */
    static final class FJApplyer extends RecursiveAction {
        final int[] array;
        final IntProcedure f;
        final int lo;
        final int hi;
        final int gran;
        FJApplyer next;

        FJApplyer(int[] array, IntProcedure f, int lo, int hi, int gran){
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
                FJApplyer r = new FJApplyer(array, f, mid, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            for (int i = l; i < h; ++i)
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
        final MapperToInt<T> mapper;
        final IntReducer reducer;
        final int base;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJMapReducer<T> next;

        FJMapReducer(List<T> list, 
                     MapperToInt<T> mapper,
                     IntReducer reducer,
                     int base,
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
                                        base, mid, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            int x = base;
            for (int i = l; i < h; ++i)
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
     * Fork/Join version of MapReduce
     */
    static final class FJArrayMapReducer<T> extends RecursiveAction {
        final T[] array;
        final MapperToInt<T> mapper;
        final IntReducer reducer;
        final int base;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJArrayMapReducer<T> next;

        FJArrayMapReducer(T[] array, 
                          MapperToInt<T> mapper,
                          IntReducer reducer,
                          int base,
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
            FJArrayMapReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJArrayMapReducer<T> r = 
                    new FJArrayMapReducer<T>(array, mapper, reducer, 
                                             base, mid, h, g);
                r.next = right;
                right = r;
                h = mid;
                r.fork();
            }
            int x = base;
            for (int i = l; i < h; ++i)
                x = reducer.combine(x, mapper.map(array[i]));
            while (right != null) {
                right.join();
                x = reducer.combine(x, right.result);
                FJArrayMapReducer<T> next = right.next;
                right.next = null;
                right = next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of TransformReduce
     */
    static final class FJTransformReducer extends RecursiveAction {
        final int[] array;
        final IntTransformer mapper;
        final IntReducer reducer;
        final int base;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJTransformReducer next;

        FJTransformReducer(int[] array, 
                           IntTransformer mapper,
                           IntReducer reducer,
                           int base,
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
                                           base, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = base;
            for (int i = l; i < h; ++i)
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
        final int[] array;
        final int[] dest;
        final IntTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper next;

        FJMapper(int[] array, 
                 int[] dest,
                 IntTransformer mapper,
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
                    new FJMapper(array, dest, mapper, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            for (int i = l; i < h; ++i)
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
        final int[] array;
        final IntReducer reducer;
        final int base;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJReducer next;

        FJReducer(int[] array, 
                  IntReducer reducer,
                  int base,
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
                    new FJReducer(array, reducer, base, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = base;
            for (int i = l; i < h; ++i)
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
        final int[] array;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJSum next;

        FJSum(int[] array, 
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
                    new FJSum(array, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = 0;
            for (int i = l; i < h; ++i)
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
        final int[] array;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJMin next;

        FJMin(int[] array, 
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
                    new FJMin(array, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = Integer.MAX_VALUE;
            for (int i = l; i < h; ++i) {
                int y = array[i];
                if (y < x)
                    x = y;
            }
            while (right != null) {
                right.join();
                int y = right.result;
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
        final int[] array;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJMax next;

        FJMax(int[] array, 
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
                    new FJMax(array, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = Integer.MAX_VALUE;
            for (int i = l; i < h; ++i) {
                int y = array[i];
                if (y > x)
                    x = y;
            }
            while (right != null) {
                right.join();
                int y = right.result;
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
        final int[] array;
        final IntTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        int result;
        FJTransformSum next;

        FJTransformSum(int[] array, 
                       IntTransformer mapper,
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
                    new FJTransformSum(array, mapper, mid, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            int x = 0;
            for (int i = l; i < h; ++i)
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
    static final class FJPlusScan extends AsyncAction {
        final FJPlusScan parent;
        FJPlusScan left, right;
        final int[] array;
        final int lo;
        final int hi;
        final int gran;
        final boolean isLeaf;

        /**
         * A predecessor in tree (or null if none). Needed to
         * determine if a segment already has incoming cumulation
         * during first pass. For right-hand leaf nodes, this always
         * points to left sibling.  For others, it points to parent's
         * pred, in which case, when determining if cumulation is
         * ready, it descends right children to find predecessor leaf,
         * if it exists. It might not exist if the node has not yet
         * been created.
         */
        final FJPlusScan pred;

        /** Incoming cumulative sum */
        int in;

        /** Sum of this subtree */
        int out;
        
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

        static final AtomicIntegerFieldUpdater<FJPlusScan> phaseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FJPlusScan.class, "phase");

        /**
         * Sets phase to indicated bits, returning false if already
         * set.
         */
        boolean transitionTo(int bits) {
            int c;
            while (((c = phase) & bits) != bits)
                if (phaseUpdater.compareAndSet(this, c, c | bits))
                    return true;
            return false;
        }

        FJPlusScan(FJPlusScan parent,
                   FJPlusScan pred,
                   int[] array, 
                   int lo, 
                   int hi, 
                   int gran) {
            this.parent = parent;
            this.pred = pred;
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
            this.isLeaf = hi - lo <= gran;
        }
        
        public void compute() {
            boolean cumulate = (phase & CUMULATE) != 0;

            if (!isLeaf) {
                if (left == null) {
                    int mid = (lo + hi) >>> 1;
                    left =  new FJPlusScan(this, pred, array, lo, mid, gran);
                    right = new FJPlusScan(this, left, array, mid, hi, gran);
                }
                if (cumulate) { // push down sums
                    int cin = in;
                    left.in = cin;
                    right.in = cin + left.out;
                }
                // Suppress second pass forks if already triggered
                if (!cumulate || right.transitionTo(CUMULATE))
                    right.fork();
                if (!cumulate || left.transitionTo(CUMULATE))
                    left.compute();
            }

            else {
                if (!cumulate) { // try early cumulation if predecessor done
                    FJPlusScan prev = pred;
                    if (prev == null) // leftmost segment always OK
                        cumulate = true;
                    else {  // (this rarely loops unless can cumulate)
                        for (;;) {
                            if ((prev.phase & FINISHED) == 0)
                                break;
                            if (prev.isLeaf) {
                                cumulate = true;
                                break;
                            }
                            if ((prev = prev.right) == null)
                                break;
                        }
                    }
                    if (cumulate) {
                        if (!transitionTo(CUMULATE))
                            return; // lost refork race
                        int last = lo - 1;
                        in = (last < 0)? 0 : array[last];
                    }
                }
                
                if (cumulate) {
                    int cin = in;
                    int sum = cin;
                    for (int i = lo; i < hi; ++i)
                        sum = array[i] += sum;
                    out = sum - cin;
                }

                else if (hi < array.length) { // skip rightmost
                    int sum = 0;
                    for (int i = lo; i < hi; ++i)
                        sum += array[i];
                    out = sum;
                }

                // Propagate sums upward and trigger second pass
                if (transitionTo(SUMMED)) {
                    FJPlusScan p = parent;
                    while (p != null && !p.transitionTo(SUMMED)) {
                        p.out = p.left.out + p.right.out;
                        // lo is 0 for root and left spine subtrees
                        if (p.lo == 0 && p.transitionTo(CUMULATE))
                            p.fork();
                        p = p.parent;
                    }
                }

                // Propagate completion
                if (cumulate && transitionTo(FINISHED)) {
                    FJPlusScan s = this;
                    FJPlusScan p = parent;
                    while (p != null) {
                        if (p.transitionTo(FINISHED))
                            return;
                        s = p;
                        p = p.parent;
                    }
                    s.finish(); // explicit finish() call only at root
                }
            }
        }
    }


    /**
     * Fork/Join version of FindAny
     */
    static final class FJFindAny extends RecursiveAction {
        final int[] array;
        final IntPredicate pred;
        final VolatileInt result;
        final int missing;
        final int lo;
        final int hi;
        final int gran;

        FJFindAny(int[] array, 
                  IntPredicate pred,
                  VolatileInt result,
                  int missing,
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
            for (int i = lo; i < hi; ++i) {
                int x = array[i];
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
                new FJFindAny(array, pred, result, missing, mid, hi, gran);
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
        final int[] array;
        final IntPredicate pred;
        final List<Integer> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAll(int[] array, 
                  IntPredicate pred,
                  List<Integer> result,
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
            for (int i = lo; i < hi; ++i) {
                int x = array[i];
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
                new FJFindAll(array, pred, result, mid, hi, gran);
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
        final int[] a;     // Array to be sorted.
        final int ao;    // origin of the part of array we deal with
        final int[] w;     // workspace array for merge
        final int wo;    // its origin
        final int n;     // Number of elements in (sub)arrays.

        FJSorter (int[] a, int ao, int[] w, int wo, int n) {
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
        final int[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final int[] w;      // Output array.
        final int wo;

        FJMerger (int[] a, int lo, int ln, int ro, int rn, int[] w, int wo) {
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
                int split = a[ls];
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
    static void quickSort(int[] a, int lo, int hi) {
        // If under threshold, use insertion sort
        if (hi - lo <= INSERTION_SORT_THRESHOLD) {
            for (int i = lo + 1; i <= hi; i++) {
                int t = a[i];
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
            int t = a[lo]; a[lo] = a[mid]; a[mid] = t;
        }
        if (a[mid] > a[hi]) {
            int t = a[mid]; a[mid] = a[hi]; a[hi] = t;
            if (a[lo] > a[mid]) {
                t = a[lo]; a[lo] = a[mid]; a[mid] = t;
            }
        }
        
        int pivot = a[mid];
        int left = lo+1; 
        int right = hi-1;
        for (;;) {
            while (pivot < a[right]) 
                --right;
            while (left < right && pivot >= a[left]) 
                ++left;
            if (left < right) {
                int t = a[left]; a[left] = a[right]; a[right] = t;
                --right;
            }
            else break;
        }
        quickSort(a, lo,    left);
        quickSort(a, left+1, hi);
    }



}