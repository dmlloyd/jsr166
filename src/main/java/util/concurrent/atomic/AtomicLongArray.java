/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;

/**
 * An AtomicLongArray maintains a <tt>long</tt> array in which
 * elements may be updated atomically, and are accessed using
 * <tt>volatile</tt> semantics.
 * @since 1.5
 * @author Doug Lea
 */
public final class AtomicLongArray implements java.io.Serializable { 
    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private static final int base = unsafe.arrayBaseOffset(long[].class);
    private static final int scale = unsafe.arrayIndexScale(long[].class);
    private final long[] array;

    private final long rawIndex(int i) {
        if (i < 0 || i >= array.length)
            throw new IndexOutOfBoundsException("index " + i);
        return base + i * scale;
    }

    /**
     * Create a new AtomicLongArray of given length.
     * @param length the length of the array
     */
    public AtomicLongArray(int length) {
        array = new long[length];
    }

    /**
     * Return the length of the array.
     */
    public int length() {
        return array.length;
    }

    /**
     * Get the current value at position <tt>i</tt>.
     *
     * @param i the index
     * @return the current value
     */
    public long get(int i) {
        return unsafe.getLongVolatile(array, rawIndex(i));
    }
 
    /**
     * Set the element at position <tt>i</tt> to the given value.
     *
     * @param i the index
     * @param newValue the new value
     */
    public void set(int i, long newValue) {
        unsafe.putLongVolatile(array, rawIndex(i), newValue);
    }
  
    /**
     * Set the element at position <tt>i</tt> to the given value and return the
     * old value.
     *
     * @param i the index
     * @param newValue the new value
     * @return the previous value
     */
    public long getAndSet(int i, long newValue) {
        while (true) {
            long current = get(i);
            if (compareAndSet(i, current, newValue))
                return current;
        }
    }
  
    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public boolean compareAndSet(int i, long expect, long update) {
        return unsafe.compareAndSwapLong(array, rawIndex(i), 
                                         expect, update);
    }

    /**
     * Atomically set the value to the given updated value
     * if the current value <tt>==</tt> the expected value.
     * May fail spuriously.
     * @param i the index
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public boolean weakCompareAndSet(int i, long expect, long update) {
        return compareAndSet(i, expect, update);
    }

    /**
     * Atomically increment by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the previous value;
     */
    public long getAndincrement(int i) {
        while (true) {
            long current = get(i);
            long next = current + 1;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  
    /**
     * Atomically decrement by one the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the previous value;
     */
    public long getAndDecrement(int i) {
        while (true) {
            long current = get(i);
            long next = current - 1;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  
    /**
     * Atomically add the given value to element at index <tt>i</tt>.
     *
     * @param i the index
     * @param delta the value to add
     * @return the previous value;
     */
    public long getAndAdd(int i, long delta) {
        while (true) {
            long current = get(i);
            long next = current + delta;
            if (compareAndSet(i, current, next))
                return current;
        }
    }
  

    /**
     * Atomically increment the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the updated value;
     */
    public long incrementAndGet(int i) {
        while (true) {
            long current = get(i);
            long next = current + 1;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  
    /**
     * Atomically decrement the element at index <tt>i</tt>.
     *
     * @param i the index
     * @return the updated value;
     */
    public long decrementAndGet(int i) {
        while (true) {
            long current = get(i);
            long next = current - 1;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  
    /**
     * Atomically add the given value to element at index <tt>i</tt>.
     *
     * @param i the index
     * @param delta the value to add
     * @return the updated value;
     */
    public long addAndGet(int i, long delta) {
        while (true) {
            long current = get(i);
            long next = current + delta;
            if (compareAndSet(i, current, next))
                return next;
        }
    }
  

}
