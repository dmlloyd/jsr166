/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.atomic;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * An AtomicIntegerFieldUpdater is a reflection-based utility that
 * enables atomic updates to designated integer fields of designated
 * classes.  It is designed for use in atomic data structures in which
 * several fields of the same node are independently subject
 * to atomic updates. 
 * <p> Note the weaker guarantees of the <code>compareAndSet<code>
 * method in this class than in other atomic classes. Because this
 * class cannot ensure that all uses of the field are appropriate for
 * purposes of atomic access, it can guarantee atomicity and volatile
 * semantics only with respect to other invocations of
 * <code>compareAndSet<code> and <tt>set</tt>.
 */

public class  AtomicIntegerFieldUpdater<T> { 
    private static final Unsafe unsafe =  Unsafe.getUnsafe();
    private final long offset;

    /**
     * Create an updater for objects with the given field.  The odd
     * nature of the constructor arguments are a result of needing
     * sufficient information to check that reflective types and
     * generic types match.
     * @param ta an array (normally of length 0) of type T (the class
     * of the objects holding the field).
     * @param fieldName the name of the field to be updated.
     * @throws IllegalArgumentException if the field is not a
     * volatile integer type.
     * @throws RuntimeException with an nested reflection-based
     * exception if the class does not hold field or is the wrong type.
     **/
    public AtomicIntegerFieldUpdater(T[] ta, String fieldName) {
        Field field = null;
        try {
            Class tclass = ta.getClass().getComponentType();
            field = tclass.getDeclaredField(fieldName);
        }
        catch(Exception ex) {
            throw new RuntimeException(ex);
        }

        Class fieldt = field.getType();
        if (fieldt != int.class) 
            throw new IllegalArgumentException("Must be integer type");

        if (!Modifier.isVolatile(field.getModifiers()))
            throw new IllegalArgumentException("Must be volatile type");

        offset = unsafe.objectFieldOffset(field);
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     **/

    public final boolean compareAndSet(T obj, int expect, int update) {
        return unsafe.compareAndSwapInt(obj, offset, expect, update);
    }

    /**
     * Atomically set the value of the field of the given object managed
     * by this Updater to the given updated value if the current value
     * <tt>==</tt> the expected value. This method is guaranteed to be
     * atomic with respect to other calls to <tt>compareAndSet</tt> and
     * <tt>set</tt>, but not necessarily with respect to other
     * changes in the field.
     * @return true if successful.
     * @throws ClassCastException if <tt>obj</tt> is not an instance
     * of the class possessing the field established in the constructor.
     **/

    public final boolean weakCompareAndSet(T obj, int expect, int update) {
        return unsafe.compareAndSwapInt(obj, offset, expect, update);
    }

    /**
     * Set the field of the given object managed by this updater. This
     * operation is guaranteed to act as a volatile store with respect
     * to subsequent invocations of <tt>compareAndSet</tt>.
     */
    public final void set(T obj, int newValue) {
        // Unsafe puts do not know about barriers, so manually apply
        unsafe.storeStoreBarrier();
        unsafe.putInt(obj, offset, newValue); 
        unsafe.storeLoadBarrier();
    }

    /**
     * Get the current value held in the field by the given object.
     */
    public final int get(T obj) {
        // Unsafe gets do not know about barriers, so manually apply
        int v = unsafe.getInt(obj, offset); 
        unsafe.loadLoadBarrier();
        return v;
    }

    /**
     * Set to the given value and return the old value
     **/
    public int getAndSet(T obj, int newValue) {
        for (;;) {
            int current = get(obj);
            if (compareAndSet(obj, current, newValue))
                return current;
        }
    }

    /**
     * Atomically increment the current value.
     * @return the previous value;
     **/
    public int getAndIncrement(T obj) {
        for (;;) {
            int current = get(obj);
            int next = current+1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically decrement the current value.
     * @return the previous value;
     **/
    public int getAndDecrement(T obj) {
        for (;;) {
            int current = get(obj);
            int next = current-1;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }
  
  
    /**
     * Atomically add the given value to current value.
     * @return the previous value;
     **/
    public int getAndAdd(T obj, int y) {
        for (;;) {
            int current = get(obj);
            int next = current+y;
            if (compareAndSet(obj, current, next))
                return current;
        }
    }

}

