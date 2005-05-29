/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group.  Adapted and released, under explicit permission,
 * from JDK ArrayList.java which carries the following copyright:
 *
 * Copyright 1997 by Sun Microsystems, Inc.,
 * 901 San Antonio Road, Palo Alto, California, 94303, U.S.A.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Sun Microsystems, Inc. ("Confidential Information").  You
 * shall not disclose such Confidential Information and shall use
 * it only in accordance with the terms of the license agreement
 * you entered into with Sun.
 */

package java.util.concurrent;
import java.util.*;

/**
 * A thread-safe variant of {@link java.util.ArrayList} in which all mutative
 * operations (<tt>add</tt>, <tt>set</tt>, and so on) are implemented by
 * making a fresh copy of the underlying array.
 *
 * <p> This is ordinarily too costly, but may be <em>more</em> efficient
 * than alternatives when traversal operations vastly outnumber
 * mutations, and is useful when you cannot or don't want to
 * synchronize traversals, yet need to preclude interference among
 * concurrent threads.  The "snapshot" style iterator method uses a
 * reference to the state of the array at the point that the iterator
 * was created. This array never changes during the lifetime of the
 * iterator, so interference is impossible and the iterator is
 * guaranteed not to throw <tt>ConcurrentModificationException</tt>.
 * The iterator will not reflect additions, removals, or changes to
 * the list since the iterator was created.  Element-changing
 * operations on iterators themselves (<tt>remove</tt>, <tt>set</tt>, and
 * <tt>add</tt>) are not supported. These methods throw
 * <tt>UnsupportedOperationException</tt>.
 *
 * <p>All elements are permitted, including <tt>null</tt>.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class CopyOnWriteArrayList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8673264195747942595L;

    /**
     * The held array. Directly accessed only within synchronized
     * methods.
     */
    private volatile transient E[] array;

    /**
     * Accessor to the array intended to be called from
     * within unsynchronized read-only methods
     */
    private E[] array() { return array; }

    /**
     * Creates an empty list.
     */
    public CopyOnWriteArrayList() {
        array = (E[]) new Object[0];
    }

    /**
     * Creates a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection of initially held elements
     * @throws NullPointerException if the specified collection is null
     */
    public CopyOnWriteArrayList(Collection<? extends E> c) {
        array = (E[]) new Object[c.size()];
        Iterator<? extends E> i = c.iterator();
        int size = 0;
        while (i.hasNext())
            array[size++] = i.next();
    }

    /**
     * Creates a list holding a copy of the given array.
     *
     * @param toCopyIn the array (a copy of this array is used as the
     *        internal array)
     * @throws NullPointerException if the specified array is null
     */
    public CopyOnWriteArrayList(E[] toCopyIn) {
        copyIn(toCopyIn, 0, toCopyIn.length);
    }

    /**
     * Replaces the held array with a copy of the <tt>n</tt> elements
     * of the provided array, starting at position <tt>first</tt>.  To
     * copy an entire array, call with arguments (array, 0,
     * array.length).
     * @param toCopyIn the array. A copy of the indicated elements of
     * this array is used as the internal array.
     * @param first The index of first position of the array to
     * start copying from.
     * @param n the number of elements to copy. This will be the new size of
     * the list.
     */
    private synchronized void copyIn(E[] toCopyIn, int first, int n) {
        array = (E[]) new Object[n];
        System.arraycopy(toCopyIn, first, array, 0, n);
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
        return array().length;
    }

    /**
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    public boolean contains(Object o) {
        E[] elementData = array();
        int len = elementData.length;
        return indexOf(o, elementData, len) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    public int indexOf(Object o) {
        E[] elementData = array();
        int len = elementData.length;
        return indexOf(o, elementData, len);
    }

    /**
     * static version allows repeated call without needing
     * to grab lock for array each time
     */
    private static int indexOf(Object o, Object[] elementData, int len) {
        if (o == null) {
            for (int i = 0; i < len; i++)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = 0; i < len; i++)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns the index of the first occurrence of the specified element in
     * this list, searching forwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(i&nbsp;&gt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param e element to search for
     * @param index index to start searching from
     * @return the index of the first occurrence of the element in
     *         this list at position <tt>index</tt> or later in the list;
     *         <tt>-1</tt> if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    public int indexOf(E e, int index) {
        E[] elementData = array();
        int elementCount = elementData.length;

        if (e == null) {
            for (int i = index ; i < elementCount ; i++)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = index ; i < elementCount ; i++)
                if (e.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public int lastIndexOf(Object o) {
        E[] elementData = array();
        int len = elementData.length;
        return lastIndexOf(o, elementData, len);
    }

    private static int lastIndexOf(Object o, Object[] elementData, int len) {
        if (o == null) {
            for (int i = len-1; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = len-1; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element in
     * this list, searching backwards from <tt>index</tt>, or returns -1 if
     * the element is not found.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(i&nbsp;&lt;=&nbsp;index&nbsp;&amp;&amp;&nbsp;(e==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;e.equals(get(i))))</tt>,
     * or -1 if there is no such index.
     *
     * @param e element to search for
     * @param index index to start searching backwards from
     * @return the index of the last occurrence of the element at position
     *         less than or equal to <tt>index</tt> in this list;
     *         -1 if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is greater
     *         than or equal to the current size of this list
     */
    public int lastIndexOf(E e, int index) {
        // needed in order to compile on 1.2b3
        E[] elementData = array();
        if (e == null) {
            for (int i = index; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = index; i >= 0; i--)
                if (e.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns a shallow copy of this list.  (The elements themselves
     * are not copied.)
     *
     * @return a clone of this list
     */
    public Object clone() {
        try {
            E[] elementData = array();
            CopyOnWriteArrayList<E> v = (CopyOnWriteArrayList<E>)super.clone();
            v.array = (E[]) new Object[elementData.length];
            System.arraycopy(elementData, 0, v.array, 0, elementData.length);
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all the elements in this list
     */
    public Object[] toArray() {
        Object[] elementData = array();
        Object[] result = new Object[elementData.length];
        System.arraycopy(elementData, 0, result, 0, elementData.length);
        return result;
    }

    /**
     * Returns an array containing all of the elements in this list in
     * proper sequence (from first to last element); the runtime type of
     * the returned array is that of the specified array.  If the list fits
     * in the specified array, it is returned therein.  Otherwise, a new
     * array is allocated with the runtime type of the specified array and
     * the size of this list.
     *
     * <p>If this list fits in the specified array with room to spare
     * (i.e., the array has more elements than this list), the element in
     * the array immediately following the end of the list is set to
     * <tt>null</tt>.  (This is useful in determining the length of this
     * list <i>only</i> if the caller knows that this list does not contain
     * any null elements.)
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose <tt>x</tt> is a list known to contain only strings.
     * The following code can be used to dump the list into a newly
     * allocated array of <tt>String</tt>:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that <tt>toArray(new Object[0])</tt> is identical in function to
     * <tt>toArray()</tt>.
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing all the elements in this list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T a[]) {
        E[] elementData = array();

        if (a.length < elementData.length)
            a = (T[])
            java.lang.reflect.Array.newInstance(a.getClass().getComponentType(),
            elementData.length);

        System.arraycopy(elementData, 0, a, 0, elementData.length);

        if (a.length > elementData.length)
            a[elementData.length] = null;

        return a;
    }

    // Positional Access Operations

    /**
     * {@inheritDoc}
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        return array()[index];
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized E set(int index, E element) {
        int len = array.length;
        E oldValue = array[index];

        boolean same = (oldValue == element ||
        (element != null && element.equals(oldValue)));
        if (!same) {
            E[] newArray = (E[]) new Object[len];
            System.arraycopy(array, 0, newArray, 0, len);
            newArray[index] = element;
            array = newArray;
        }
        return oldValue;
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * @param element element to be appended to this list
     * @return <tt>true</tt> (as per the spec for {@link Collection#add})
     */
    public synchronized boolean add(E element) {
        int len = array.length;
        E[] newArray = (E[]) new Object[len+1];
        System.arraycopy(array, 0, newArray, 0, len);
        newArray[len] = element;
        array = newArray;
        return true;
    }

    /**
     * Inserts the specified element at the specified position in this
     * list. Shifts the element currently at that position (if any) and
     * any subsequent elements to the right (adds one to their indices).
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized void add(int index, E element) {
        int len = array.length;
        if (index > len || index < 0)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+len);

        E[] newArray = (E[]) new Object[len+1];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index+1, len - index);
        array = newArray;
    }

    /**
     * Removes the element at the specified position in this list.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).  Returns the element that was removed from the list.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized E remove(int index) {
        int len = array.length;
        E oldValue = array[index];
        E[] newArray = (E[]) new Object[len-1];
        System.arraycopy(array, 0, newArray, 0, index);
        int numMoved = len - index - 1;
        if (numMoved > 0)
            System.arraycopy(array, index+1, newArray, index, numMoved);
        array = newArray;
        return oldValue;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    public synchronized boolean remove(Object o) {
        int len = array.length;
        if (len == 0) return false;

        // Copy while searching for element to remove
        // This wins in the normal case of element being present

        int newlen = len-1;
        E[] newArray = (E[]) new Object[newlen];

        for (int i = 0; i < newlen; ++i) {
            if (o == array[i] ||
            (o != null && o.equals(array[i]))) {
                // found one;  copy remaining and exit
                for (int k = i + 1; k < len; ++k) newArray[k-1] = array[k];
                array = newArray;
                return true;
            } else
                newArray[i] = array[i];
        }
        // special handling for last cell

        if (o == array[newlen] ||
        (o != null && o.equals(array[newlen]))) {
            array = newArray;
            return true;
        } else
            return false; // throw away copy
    }

    /**
     * Removes from this list all of the elements whose index is between
     * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.
     * Shifts any succeeding elements to the left (reduces their index).
     * This call shortens the list by <tt>(toIndex - fromIndex)</tt> elements.
     * (If <tt>toIndex==fromIndex</tt>, this operation has no effect.)
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     * @throws IndexOutOfBoundsException if fromIndex or toIndex out of
     *              range (fromIndex &lt; 0 || fromIndex &gt;= size() || toIndex
     *              &gt; size() || toIndex &lt; fromIndex)
     */
    private synchronized void removeRange(int fromIndex, int toIndex) {
        int len = array.length;

        if (fromIndex < 0 || fromIndex >= len ||
        toIndex > len || toIndex < fromIndex)
            throw new IndexOutOfBoundsException();

        int numMoved = len - toIndex;
        int newlen = len - (toIndex-fromIndex);
        E[] newArray = (E[]) new Object[newlen];
        System.arraycopy(array, 0, newArray, 0, fromIndex);
        System.arraycopy(array, toIndex, newArray, fromIndex, numMoved);
        array = newArray;
    }

    /**
     * Append the element if not present.
     *
     * @param element element to be added to this list, if absent
     * @return <tt>true</tt> if the element was added
     */
    public synchronized boolean addIfAbsent(E element) {
        // Copy while checking if already present.
        // This wins in the most common case where it is not present
        int len = array.length;
        E[] newArray = (E[]) new Object[len + 1];
        for (int i = 0; i < len; ++i) {
            if (element == array[i] ||
            (element != null && element.equals(array[i])))
                return false; // exit, throwing away copy
            else
                newArray[i] = array[i];
        }
        newArray[len] = element;
        array = newArray;
        return true;
    }

    /**
     * Returns <tt>true</tt> if this list contains all of the elements of the
     * specified collection.
     *
     * @param c collection to be checked for containment in this list
     * @return <tt>true</tt> if this list contains all of the elements of the
     *         specified collection
     * @throws NullPointerException if the specified collection is null
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
        E[] elementData = array();
        int len = elementData.length;
        Iterator e = c.iterator();
        while (e.hasNext())
            if (indexOf(e.next(), elementData, len) < 0)
                return false;

        return true;
    }

    /**
     * Removes from this list all of its elements that are contained in
     * the specified collection. This is a particularly expensive operation
     * in this class because of the need for an internal temporary array.
     *
     * @param c collection containing elements to be removed from this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public synchronized boolean removeAll(Collection<?> c) {
        E[] elementData = array;
        int len = elementData.length;
        if (len == 0) return false;

        // temp array holds those elements we know we want to keep
        E[] temp = (E[]) new Object[len];
        int newlen = 0;
        for (int i = 0; i < len; ++i) {
            E element = elementData[i];
            if (!c.contains(element)) {
                temp[newlen++] = element;
            }
        }

        if (newlen == len) return false;

        //  copy temp as new array
        E[] newArray = (E[]) new Object[newlen];
        System.arraycopy(temp, 0, newArray, 0, newlen);
        array = newArray;
        return true;
    }

    /**
     * Retains only the elements in this list that are contained in the
     * specified collection.  In other words, removes from this list all of
     * its elements that are not contained in the specified collection.
     *
     * @param c collection containing elements to be retained in this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     */
    public synchronized boolean retainAll(Collection<?> c) {
        E[] elementData = array;
        int len = elementData.length;
        if (len == 0) return false;

        E[] temp = (E[]) new Object[len];
        int newlen = 0;
        for (int i = 0; i < len; ++i) {
            E element = elementData[i];
            if (c.contains(element)) {
                temp[newlen++] = element;
            }
        }

        if (newlen == len) return false;

        E[] newArray = (E[]) new Object[newlen];
        System.arraycopy(temp, 0, newArray, 0, newlen);
        array = newArray;
        return true;
    }

    /**
     * Appends all of the elements in the specified collection that
     * are not already contained in this list, to the end of
     * this list, in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param c collection containing elements to be added to this list
     * @return the number of elements added
     * @throws NullPointerException if the specified collection is null
     * @see #addIfAbsent(Object)
     */
    public synchronized int addAllAbsent(Collection<? extends E> c) {
        int numNew = c.size();
        if (numNew == 0) return 0;

        E[] elementData = array;
        int len = elementData.length;

        E[] temp = (E[]) new Object[numNew];
        int added = 0;
        Iterator<? extends E> e = c.iterator();
        while (e.hasNext()) {
            E element = e.next();
            if (indexOf(element, elementData, len) < 0) {
                if (indexOf(element, temp, added) < 0) {
                    temp[added++] = element;
                }
            }
        }

        if (added == 0) return 0;

        E[] newArray = (E[]) new Object[len+added];
        System.arraycopy(elementData, 0, newArray, 0, len);
        System.arraycopy(temp, 0, newArray, len, added);
        array = newArray;
        return added;
    }

    /**
     * Removes all of the elements from this list.
     * The list will be empty after this call returns.
     */
    public synchronized void clear() {
        array = (E[]) new Object[0];
    }

    /**
     * Appends all of the elements in the specified collection to the end
     * of this list, in the order that they are returned by the specified
     * collection's iterator.
     *
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @see #add(Object)
     */
    public synchronized boolean addAll(Collection<? extends E> c) {
        int numNew = c.size();
        if (numNew == 0) return false;

        int len = array.length;
        E[] newArray = (E[]) new Object[len+numNew];
        System.arraycopy(array, 0, newArray, 0, len);
        Iterator<? extends E> e = c.iterator();
        for (int i=0; i<numNew; i++)
            newArray[len++] = e.next();
        array = newArray;

        return true;
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in this list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element
     *        from the specified collection
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     * @see #add(int,Object)
     */
    public synchronized boolean addAll(int index, Collection<? extends E> c) {
        int len = array.length;
        if (index > len || index < 0)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+len);

        int numNew = c.size();
        if (numNew == 0) return false;

        E[] newArray = (E[]) new Object[len+numNew];
        System.arraycopy(array, 0, newArray, 0, len);
        int numMoved = len - index;
        if (numMoved > 0)
            System.arraycopy(array, index, newArray, index + numNew, numMoved);
        Iterator<? extends E> e = c.iterator();
        for (int i=0; i<numNew; i++)
            newArray[index++] = e.next();
        array = newArray;

        return true;
    }

    /**
     * Checks if the given index is in range.  If not, throws an appropriate
     * runtime exception.
     */
    private void rangeCheck(int index, int length) {
        if (index >= length || index < 0)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+ length);
    }

    /**
     * Save the state of the list to a stream (i.e., serialize it).
     *
     * @serialData The length of the array backing the list is emitted
     *               (int), followed by all of its elements (each an Object)
     *               in the proper order.
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{

        // Write out element count, and any hidden stuff
        s.defaultWriteObject();

        E[] elementData = array();
        // Write out array length
        s.writeInt(elementData.length);

        // Write out all elements in the proper order.
        for (int i=0; i<elementData.length; i++)
            s.writeObject(elementData[i]);
    }

    /**
     * Reconstitute the list from a stream (i.e., deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in array length and allocate array
        int arrayLength = s.readInt();
        E[] elementData = (E[]) new Object[arrayLength];

        // Read in all elements in the proper order.
        for (int i=0; i<elementData.length; i++)
            elementData[i] = (E) s.readObject();
        array = elementData;
    }

    /**
     * Returns a string representation of this list, containing
     * the String representation of each element.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        Iterator e = iterator();
        buf.append("[");
        int maxIndex = size() - 1;
        for (int i = 0; i <= maxIndex; i++) {
            buf.append(String.valueOf(e.next()));
            if (i < maxIndex)
                buf.append(", ");
        }
        buf.append("]");
        return buf.toString();
    }

    /**
     * Compares the specified object with this list for equality.
     * Returns true if and only if the specified object is also a {@link
     * List}, both lists have the same size, and all corresponding pairs
     * of elements in the two lists are <em>equal</em>.  (Two elements
     * <tt>e1</tt> and <tt>e2</tt> are <em>equal</em> if <tt>(e1==null ?
     * e2==null : e1.equals(e2))</tt>.)  In other words, two lists are
     * defined to be equal if they contain the same elements in the same
     * order.
     *
     * @param o the object to be compared for equality with this list
     * @return <tt>true</tt> if the specified object is equal to this list
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        List<E> l2 = (List<E>)(o);
        if (size() != l2.size())
            return false;

        ListIterator<E> e1 = listIterator();
        ListIterator<E> e2 = l2.listIterator();
        while (e1.hasNext()) {
            E o1 = e1.next();
            E o2 = e2.next();
            if (!(o1==null ? o2==null : o1.equals(o2)))
                return false;
        }
        return true;
    }

    /**
     * Returns the hash code value for this list.
     *
     * <p> This implementation uses the definition in {@link
     * List#hashCode}.
     * @return the hash code
     */
    public int hashCode() {
        int hashCode = 1;
        Iterator<E> i = iterator();
        while (i.hasNext()) {
            E obj = i.next();
            hashCode = 31*hashCode + (obj==null ? 0 : obj.hashCode());
        }
        return hashCode;
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt> method.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new COWIterator<E>(array(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned iterator provides a snapshot of the state of the list
     * when the iterator was constructed. No synchronization is needed while
     * traversing the iterator. The iterator does <em>NOT</em> support the
     * <tt>remove</tt>, <tt>set</tt> or <tt>add</tt> methods.
     */
    public ListIterator<E> listIterator() {
        return new COWIterator<E>(array(), 0);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The list iterator returned by this implementation will throw an
     * <tt>UnsupportedOperationException</tt> in its <tt>remove</tt>,
     * <tt>set</tt> and <tt>add</tt> methods.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(final int index) {
        E[] elementData = array();
        int len = elementData.length;
        if (index<0 || index>len)
            throw new IndexOutOfBoundsException("Index: "+index);

        return new COWIterator<E>(array(), index);
    }

    private static class COWIterator<E> implements ListIterator<E> {

        /** Snapshot of the array **/
        private final E[] array;

        /**
         * Index of element to be returned by subsequent call to next.
         */
        private int cursor;

        private COWIterator(E[] elementArray, int initialCursor) {
            array = elementArray;
            cursor = initialCursor;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        public E next() {
            try {
                return array[cursor++];
            } catch (IndexOutOfBoundsException ex) {
                throw new NoSuchElementException();
            }
        }

        public E previous() {
            try {
                return array[--cursor];
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor-1;
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>remove</tt>
         *         is not supported by this iterator.
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>set</tt>
         *         is not supported by this iterator.
         */
        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        /**
         * Not supported. Always throws UnsupportedOperationException.
         * @throws UnsupportedOperationException always; <tt>add</tt>
         *         is not supported by this iterator.
         */
        public void add(E e) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns a view of the portion of this list between <tt>fromIndex</tt>,
     * inclusive, and <tt>toIndex</tt>, exclusive.  The returned list is
     * backed by this list, so changes in the returned list are reflected in
     * this list, and vice-versa.  While mutative operations are supported,
     * they are probably not very useful for CopyOnWriteArrayLists.
     *
     * <p>The semantics of the list returned by this method become undefined if
     * the backing list (i.e., this list) is <i>structurally modified</i> in
     * any way other than via the returned list.  (Structural modifications are
     * those that change the size of the list, or otherwise perturb it in such
     * a fashion that iterations in progress may yield incorrect results.)
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized List<E> subList(int fromIndex, int toIndex) {
        // synchronized since sublist constructor depends on it.
        int len = array.length;
        if (fromIndex<0 || toIndex>len  || fromIndex>toIndex)
            throw new IndexOutOfBoundsException();
        return new COWSubList<E>(this, fromIndex, toIndex);
    }

    private static class COWSubList<E> extends AbstractList<E> {

        /*
          This class extends AbstractList merely for convenience, to
          avoid having to define addAll, etc. This doesn't hurt, but
          is wasteful.  This class does not need or use modCount
          mechanics in AbstractList, but does need to check for
          concurrent modification using similar mechanics.  On each
          operation, the array that we expect the backing list to use
          is checked and updated.  Since we do this for all of the
          base operations invoked by those defined in AbstractList,
          all is well.  While inefficient, this is not worth
          improving.  The kinds of list operations inherited from
          AbstractList are already so slow on COW sublists that
          adding a bit more space/time doesn't seem even noticeable.
         */

        private final CopyOnWriteArrayList<E> l;
        private final int offset;
        private int size;
        private E[] expectedArray;

        private COWSubList(CopyOnWriteArrayList<E> list,
        int fromIndex, int toIndex) {
            l = list;
            expectedArray = l.array();
            offset = fromIndex;
            size = toIndex - fromIndex;
        }

        // only call this holding l's lock
        private void checkForComodification() {
            if (l.array != expectedArray)
                throw new ConcurrentModificationException();
        }

        // only call this holding l's lock
        private void rangeCheck(int index) {
            if (index<0 || index>=size)
                throw new IndexOutOfBoundsException("Index: "+index+ ",Size: "+size);
        }


        public E set(int index, E element) {
            synchronized(l) {
                rangeCheck(index);
                checkForComodification();
                E x = l.set(index+offset, element);
                expectedArray = l.array;
                return x;
            }
        }

        public E get(int index) {
            synchronized(l) {
                rangeCheck(index);
                checkForComodification();
                return l.get(index+offset);
            }
        }

        public int size() {
            synchronized(l) {
                checkForComodification();
                return size;
            }
        }

        public void add(int index, E element) {
            synchronized(l) {
                checkForComodification();
                if (index<0 || index>size)
                    throw new IndexOutOfBoundsException();
                l.add(index+offset, element);
                expectedArray = l.array;
                size++;
            }
        }

        public void clear() {
            synchronized(l) {
                checkForComodification();
                l.removeRange(offset, offset+size);
                expectedArray = l.array;
                size = 0;
            }
        }

        public E remove(int index) {
            synchronized(l) {
                rangeCheck(index);
                checkForComodification();
                E result = l.remove(index+offset);
                expectedArray = l.array;
                size--;
                return result;
            }
        }

        public Iterator<E> iterator() {
            synchronized(l) {
                checkForComodification();
                return new COWSubListIterator<E>(l, 0, offset, size);
            }
        }

        public ListIterator<E> listIterator(final int index) {
            synchronized(l) {
                checkForComodification();
                if (index<0 || index>size)
                    throw new IndexOutOfBoundsException("Index: "+index+", Size: "+size);
                return new COWSubListIterator<E>(l, index, offset, size);
            }
        }

        public List<E> subList(int fromIndex, int toIndex) {
            synchronized(l) {
                checkForComodification();
                if (fromIndex<0 || toIndex>size)
                    throw new IndexOutOfBoundsException();
                return new COWSubList<E>(l, fromIndex+offset, toIndex+offset);
            }
        }

    }


    private static class COWSubListIterator<E> implements ListIterator<E> {
        private final ListIterator<E> i;
        private final int index;
        private final int offset;
        private final int size;
        private COWSubListIterator(List<E> l, int index, int offset, int size) {
            this.index = index;
            this.offset = offset;
            this.size = size;
            i = l.listIterator(index+offset);
        }

        public boolean hasNext() {
            return nextIndex() < size;
        }

        public E next() {
            if (hasNext())
                return i.next();
            else
                throw new NoSuchElementException();
        }

        public boolean hasPrevious() {
            return previousIndex() >= 0;
        }

        public E previous() {
            if (hasPrevious())
                return i.previous();
            else
                throw new NoSuchElementException();
        }

        public int nextIndex() {
            return i.nextIndex() - offset;
        }

        public int previousIndex() {
            return i.previousIndex() - offset;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void set(E e) {
            throw new UnsupportedOperationException();
        }

        public void add(E e) {
            throw new UnsupportedOperationException();
        }
    }

}
