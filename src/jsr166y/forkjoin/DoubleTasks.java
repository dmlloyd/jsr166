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
 * Parallel double operations on collections and arrays. This class is
 * a stand-in for functionality that will probably be supported in
 * some other way in Java 7.
 */
public class DoubleTasks {
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
                                 double[] array, 
                                 DoubleProcedure proc) {
        int n = array.length;
        pool.invoke(new FJApplyer(array, proc, 0, n-1, defaultGranularity(pool, n)));
    }
    
    /**
     * Returns reduction of given array
     * @param pool the pool
     * @param array the array
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static double reduce(ForkJoinPool pool,
                                double[] array, 
                                DoubleReducer reducer,
                                double base) {
        int n = array.length;
        FJReducer r = new FJReducer(array,reducer, base,
                                    0, n-1, defaultGranularity(pool, n));
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
    public static <T> double reduce(ForkJoinPool pool,
                             List<T> list, 
                             MapperToDouble<T> mapper,
                             DoubleReducer reducer,
                             double base) {
        int n = list.size();
        FJMapReducer<T> r =
            new FJMapReducer<T>(list, mapper, reducer, base,
                                0, n-1, defaultGranularity(pool, n));
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
    public static <T> double reduce(ForkJoinPool pool,
                             double[] array, 
                             DoubleTransformer mapper,
                             DoubleReducer reducer,
                             double base) {
        int n = array.length;
        FJTransformReducer r =
            new FJTransformReducer(array, mapper, reducer, base,
                                   0, n-1, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns a array mapping each element of given array using mapper
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     */
    public static double[] map(ForkJoinPool pool,
                               double[] array, 
                               DoubleTransformer mapper) {
        int n = array.length;
        double[] dest = new double[n];
        pool.invoke(new FJMapper(array, dest, mapper,  
                                 0, n-1, defaultGranularity(pool, n)));
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
    public static double findAny(ForkJoinPool pool,
                              double[] array, 
                              DoublePredicate  pred,
                              double missing) {
        int n = array.length;
        VolatileDouble result = new VolatileDouble(missing);
        pool.invoke(new FJFindAny(array, pred, result, missing,
                                  0, n-1, defaultGranularity(pool, n)));
        return result.value;
    }

    static final class VolatileDouble {
        volatile double value;
        VolatileDouble(double v) { value = v; }
    }

    /**
     * Returns a list of all elements of the array matching pred
     * @param pool the pool
     * @param array the array
     * @param pred the predicate
     */
    public static List<Double> findAll(ForkJoinPool pool,
                                        double[] array, 
                                        DoublePredicate pred) {
        int n = array.length;
        Vector<Double> dest = new Vector<Double>(); // todo: use smarter list
        pool.invoke(new FJFindAll(array, pred, dest,  
                                  0, n-1, defaultGranularity(pool, n)));
        return dest;
    }

    /**
     * Returns the sum of all elements
     * @param pool the pool
     * @param array the array
     */
    public static double sum(ForkJoinPool pool, 
                             double[] array) {
        int n = array.length;
        FJSum r = new FJSum(array, 0, n-1, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns the sum of all mapped elements
     * @param pool the pool
     * @param array the array
     * @param mapper the mapper
     */
    public static double sum(ForkJoinPool pool, 
                             double[] array, 
                             DoubleTransformer mapper) {
        int n = array.length;
        FJTransformSum r = 
            new FJTransformSum(array, mapper, 0, n-1, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns the minimum of all elements, or MAX_VALUE if empty
     * @param pool the pool
     * @param array the array
     */
    public static double min(ForkJoinPool pool, 
                             double[] array) {
        int n = array.length;
        FJMin r = new FJMin(array, 0, n-1, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }

    /**
     * Returns the maximum of all elements, or MIN_VALUE if empty
     * @param pool the pool
     * @param array the array
     */
    public static double max(ForkJoinPool pool, 
                             double[] array) {
        int n = array.length;
        FJMax r = new FJMax(array, 0, n-1, defaultGranularity(pool, n));
        pool.invoke(r);
        return r.result;
    }


    /**
     * Sorts the given array
     * @param pool the pool
     * @param array the array
     */
    public static void sort(ForkJoinPool pool, double[] array) {
        int n = array.length;
        double[] workSpace = new double[n];
        pool.invoke(new FJSorter(array, 0, workSpace, 0, n));
    }


    /**
     * Fork/Join version of apply
     */
    static final class FJApplyer extends RecursiveAction {
        final double[] array;
        final DoubleProcedure f;
        final int lo;
        final int hi;
        final int gran;
        FJApplyer next;

        FJApplyer(double[] array, DoubleProcedure f, int lo, int hi, int gran){
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
        final MapperToDouble<T> mapper;
        final DoubleReducer reducer;
        final double base;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJMapReducer<T> next;

        FJMapReducer(List<T> list, 
                   MapperToDouble<T> mapper,
                   DoubleReducer reducer,
                   double base,
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
            double x = base;
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
        final double[] array;
        final DoubleTransformer mapper;
        final DoubleReducer reducer;
        final double base;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJTransformReducer next;

        FJTransformReducer(double[] array, 
                           DoubleTransformer mapper,
                           DoubleReducer reducer,
                           double base,
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
            double x = base;
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
        final double[] array;
        final double[] dest;
        final DoubleTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper next;

        FJMapper(double[] array, 
                 double[] dest,
                 DoubleTransformer mapper,
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
        final double[] array;
        final DoubleReducer reducer;
        final double base;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJReducer next;

        FJReducer(double[] array, 
                  DoubleReducer reducer,
                  double base,
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
            double x = base;
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
        final double[] array;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJSum next;

        FJSum(double[] array, 
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
            double x = 0;
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
        final double[] array;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJMin next;

        FJMin(double[] array, 
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
            double x = Double.MAX_VALUE;
            for (int i = l; i <= h; ++i)
                x = Math.min(x, array[i]);
            while (right != null) {
                right.join();
                x = Math.min(x, right.result);
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of max
     */
    static final class FJMax extends RecursiveAction {
        final double[] array;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJMax next;

        FJMax(double[] array, 
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
            double x = Double.MAX_VALUE;
            for (int i = l; i <= h; ++i)
                x = Math.max(x, array[i]);
            while (right != null) {
                right.join();
                x = Math.max(x, right.result);
                right = right.next;
            }
            result = x;
        }
    }              

    /**
     * Fork/Join version of TransformSum
     */
    static final class FJTransformSum extends RecursiveAction {
        final double[] array;
        final DoubleTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        double result;
        FJTransformSum next;

        FJTransformSum(double[] array, 
                       DoubleTransformer mapper,
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
            double x = 0;
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
     * Fork/Join version of FindAny
     */
    static final class FJFindAny extends RecursiveAction {
        final double[] array;
        final DoublePredicate pred;
        final VolatileDouble result;
        final double missing;
        final int lo;
        final int hi;
        final int gran;

        FJFindAny(double[] array, 
                  DoublePredicate pred,
                  VolatileDouble result,
                  double missing,
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
                double x = array[i];
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
        final double[] array;
        final DoublePredicate pred;
        final List<Double> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAll(double[] array, 
                  DoublePredicate pred,
                  List<Double> result,
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
                double x = array[i];
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
        final double[] a;     // Array to be sorted.
        final int ao;    // origin of the part of array we deal with
        final double[] w;     // workspace array for merge
        final int wo;    // its origin
        final int n;     // Number of elements in (sub)arrays.

        FJSorter (double[] a, int ao, double[] w, int wo, int n) {
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
        final double[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final double[] w;      // Output array.
        final int wo;

        FJMerger (double[] a, int lo, int ln, int ro, int rn, double[] w, int wo) {
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
                double split = a[ls];
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
    static void quickSort(double[] a, int lo, int hi) {
        // If under threshold, use insertion sort
        if (hi - lo <= INSERTION_SORT_THRESHOLD) {
            for (int i = lo + 1; i <= hi; i++) {
                double t = a[i];
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
            double t = a[lo]; a[lo] = a[mid]; a[mid] = t;
        }
        if (a[mid] > a[hi]) {
            double t = a[mid]; a[mid] = a[hi]; a[hi] = t;
            if (a[lo] > a[mid]) {
                t = a[lo]; a[lo] = a[mid]; a[mid] = t;
            }
        }
        
        double pivot = a[mid];
        int left = lo+1; 
        int right = hi-1;
        for (;;) {
            while (pivot < a[right]) 
                --right;
            while (left < right && pivot >= a[left]) 
                ++left;
            if (left < right) {
                double t = a[left]; a[left] = a[right]; a[right] = t;
                --right;
            }
            else break;
        }
        quickSort(a, lo,    left);
        quickSort(a, left+1, hi);
    }



}