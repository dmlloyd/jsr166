/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;

/**
 * A {@link java.util.Set} that uses an internal {@link CopyOnWriteArrayList}
 * for all of its operations.  Thus, it shares the same basic properties:
 * <ul>
 *  <li>It is best suited for applications in which set sizes generally
 *       stay small, read-only operations
 *       vastly outnumber mutative operations, and you need
 *       to prevent interference among threads during traversal.
 *  <li>It is thread-safe.
 *  <li>Mutative operations (<tt>add</tt>, <tt>set</tt>, <tt>remove</tt>, etc.)
 *      are expensive since they usually entail copying the entire underlying
 *      array.
 *  <li>Iterators do not support the mutative <tt>remove</tt> operation.
 *  <li>Traversal via iterators is fast and cannot encounter
 *      interference from other threads. Iterators rely on
 *      unchanging snapshots of the array at the time the iterators were
 *      constructed.
 * </ul>
 *
 * <p> <b>Sample Usage.</b> The following code sketch uses a
 * copy-on-write set to maintain a set of Handler objects that
 * perform some action upon state updates.
 *
 * <pre>
 * class Handler { void handle(); ... }
 *
 * class X {
 *    private final CopyOnWriteArraySet&lt;Handler&gt; handlers = new CopyOnWriteArraySet&lt;Handler&gt;();
 *    public void addHandler(Handler h) { handlers.add(h); }
 *
 *    private long internalState;
 *    private synchronized void changeState() { internalState = ...; }
 *
 *    public void update() {
 *       changeState();
 *       for (Handler handler : handlers)
 *          handler.handle();
 *    }
 * }
 * </pre>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @see CopyOnWriteArrayList
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class CopyOnWriteArraySet<E> extends AbstractSet<E>
        implements java.io.Serializable {
    private static final long serialVersionUID = 5457747651344034263L;

    private final CopyOnWriteArrayList<E> al;

    /**
     * Creates an empty set.
     */
    public CopyOnWriteArraySet() {
        al = new CopyOnWriteArrayList<E>();
    }

    /**
     * Creates a set containing all of the elements of the specified
     * collection.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection is null
     */
    public CopyOnWriteArraySet(Collection<? extends E> c) {
        al = new CopyOnWriteArrayList<E>();
        al.addAllAbsent(c);
    }

    /**
     * Returns the number of elements in this set.
     *
     * @return the number of elements in this set
     */
    public int size() {
	return al.size();
    }

    /**
     * Returns <tt>true</tt> if this set contains no elements.
     *
     * @return <tt>true</tt> if this set contains no elements
     */
    public boolean isEmpty() {
	return al.isEmpty();
    }

    /**
     * Returns <tt>true</tt> if this set contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this set
     * contains at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this set is to be tested
     * @return <tt>true</tt> if this set contains the specified element
     */
    public boolean contains(Object o) {
	return al.contains(o);
    }

    /**
     * Returns an array containing all of the elements in this set.
     * If this set makes any guarantees as to what order its elements
     * are returned by its iterator, this method must return the
     * elements in the same order.
     *
     * <p>The returned array will be "safe" in that no references to it
     * are maintained by this set.  (In other words, this method must
     * allocate a new array even if this set is backed by an array).
     * The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all the elements in this set
     */
    public Object[] toArray() {
	return al.toArray();
    }

    /**
     * Returns an array containing all of the elements in this set; the
     * runtime type of the returned array is that of the specified array.
     * If the set fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this set.
     *
     * <p>If this set fits in the specified array with room to spare
     * (i.e., the array has more elements than this set), the element in
     * the array immediately following the end of the set is set to
     * <tt>null</tt>.  (This is useful in determining the length of this
     * set <i>only</i> if the caller knows that this set does not contain
     * any null elements.)
     *
     * <p>If this set makes any guarantees as to what order its elements
     * are returned by its iterator, this method must return the elements
     * in the same order.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a set known to contain only strings.
     * The following code can be used to dump the set into a newly allocated
     * array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of this set are to be
     *        stored, if it is big enough; otherwise, a new array of the same
     *        runtime type is allocated for this purpose.
     * @return an array containing all the elements in this set
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in this
     *         set
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
	return al.toArray(a);
    }

    /**
     * Removes all of the elements from this set.
     * This set will be empty after this call returns.
     */
    public void clear() {
        al.clear();
    }

    /**
     * Removes the specified element from this set if it is present.
     * More formally, removes an element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>, if
     * this set contains such an element.  Returns <tt>true</tt> if this set
     * contained the specified element (or equivalently, if this set changed
     * as a result of the call).  (This set will not contain the specified
     * element once the call returns.)
     *
     * @param o object to be removed from this set, if present
     * @return <tt>true</tt> if this set contained the specified element
     */
    public boolean remove(Object o) {
	return al.remove(o);
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * More formally, adds the specified element, <tt>e</tt>, to this set if
     * this set contains no element <tt>e2</tt> such that <tt>(e==null ?
     * e2==null : e.equals(e2))</tt>.  If this set already contains the
     * specified element, the call leaves this set unchanged and returns
     * <tt>false</tt>.
     *
     * @param e element to be added to this set
     * @return <tt>true</tt> if this set did not already contain the specified
     *         element
     */
    public boolean add(E e) {
	return al.addIfAbsent(e);
    }

    /**
     * Returns <tt>true</tt> if this set contains all of the elements of the
     * specified collection.  If the specified collection is also a set, this
     * method returns <tt>true</tt> if it is a <i>subset</i> of this set.
     *
     * @param  c collection to be checked for containment in this set
     * @return <tt>true</tt> if this set contains all of the elements of the
     * 	       specified collection
     * @throws NullPointerException if the specified collection is null
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
	return al.containsAll(c);
    }

    /**
     * Adds all of the elements in the specified collection to this set if
     * they're not already present.  If the specified collection is also a
     * set, the <tt>addAll</tt> operation effectively modifies this set so
     * that its value is the <i>union</i> of the two sets.  The behavior of
     * this operation is unspecified if the specified collection is modified
     * while the operation is in progress.
     *
     * @param  c collection containing elements to be added to this set
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @see #add(Object)
     */
    public boolean addAll(Collection<? extends E> c) {
	return al.addAllAbsent(c) > 0;
    }

    /**
     * Removes from this set all of its elements that are contained in the
     * specified collection.  If the specified collection is also a set,
     * this operation effectively modifies this set so that its value is the
     * <i>asymmetric set difference</i> of the two sets.
     *
     * @param  c collection containing elements to be removed from this set
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws ClassCastException if the class of an element of this set
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this set contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public boolean removeAll(Collection<?> c) {
	return al.removeAll(c);
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified collection.  In other words, removes from this set all of
     * its elements that are not contained in the specified collection.  If
     * the specified collection is also a set, this operation effectively
     * modifies this set so that its value is the <i>intersection</i> of the
     * two sets.
     *
     * @param  c collection containing elements to be retained in this set
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws ClassCastException if the class of an element of this set
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this set contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public boolean retainAll(Collection<?> c) {
	return al.retainAll(c);
    }

    /**
     * Returns an iterator over the elements contained in this set
     * in the order in which these elements were added.
     *
     * <p>The returned iterator provides a snapshot of the state of the set
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt> method.
     *
     * @return an iterator over the elements in this set
     */
    public Iterator<E> iterator() {
	return al.iterator();
    }

}
