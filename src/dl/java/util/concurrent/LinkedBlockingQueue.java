package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * An unbounded queue based on linked nodes.
 **/
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /*
     * A variant of the "two lock queue" algorithm.  The putLock gates
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid
     * needing to get both locks in most cases. Also, to minimize need
     * for puts to get takeLock and vice-versa, cascading notifies are
     * used. When a put notices that it has enabled at least one take,
     * it signals taker. That taker in turn signals others if more
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and
     * iterators acquire both locks.
    */

    static class Node<E> {
        volatile E item;
        Node<E> next;
        Node(E x) { item = x; }
    }

    private final int maximumSize;
    private transient final AtomicInteger count = new AtomicInteger(0);

    private transient Node<E> head = new Node<E>(null);
    private transient Node<E> last = head;

    private transient final ReentrantLock takeLock = new ReentrantLock();
    private transient final Condition notEmpty = takeLock.newCondition();

    private transient final ReentrantLock putLock = new ReentrantLock();
    private transient final Condition notFull = putLock.newCondition();



    /**
     * Signal a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily have  takeLock.)
     */
    private void signalNotEmpty() {
        takeLock.lock();
        try {
            notEmpty.signal();
        }
        finally {
            takeLock.unlock();
        }
    }

    /**
     * Signal a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {
        putLock.lock();
        try {
            notFull.signal();
        }
        finally {
            putLock.unlock();
        }
    }

    /**
     * Create a node and link it and end of queue
     */
    private void insert(E x) {
        last = last.next = new Node<E>(x);
    }

    /**
     * Remove a node from head of queue,
     */
    private E extract() {
        Node<E> first = head.next;
        head = first;
        E x = (E)first.item;
        first.item = null;
        return x;
    }

    /**
     * Lock to prevent both puts and takes.
     */
    private void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlock to allow both puts and takes.
     */
    private void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    public LinkedBlockingQueue(int maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException();
        this.maximumSize = maximumSize;
    }

    public LinkedBlockingQueue(Collection<E> initialElements) {
        this(Integer.MAX_VALUE);
        for (Iterator<E> it = initialElements.iterator(); it.hasNext();)
            add(it.next());
    }

    public LinkedBlockingQueue(int maximumSize, Collection<E> initialElements) {
        this(maximumSize);
        for (Iterator<E> it = initialElements.iterator(); it.hasNext();)
            add(it.next());
    }


    public int size() {
        return count.get();
    }

    public int maximumSize() {
        return maximumSize;
    }

    public void put(E x) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        // Note: convention in all put/take/etc is to preset
        // local var holding count  negative to indicate failure unless set.
        int c = -1;
        putLock.lockInterruptibly();
        try {
            /*
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from
             * maximumSize. Similarly for all other uses of count in
             * other wait guards.
             */
            try {
                while (count.get() == maximumSize)
                    notFull.await();
            }
            catch (InterruptedException ie) {
                notFull.signal(); // propagate to a non-interrupted thread
                throw ie;
            }
            insert(x);
            c = count.getAndIncrement();
            if (c+1 < maximumSize)
                notFull.signal();
        }
        finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
    }

    public boolean offer(E x, long timeout, TimeUnit unit) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        putLock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        int c = -1;
        try {
            for (;;) {
                if (count.get() < maximumSize) {
                    insert(x);
                    c = count.getAndIncrement();
                    if (c+1 < maximumSize)
                        notFull.signal();
                    break;
                }
                if (nanos <= 0)
                    return false;
                try {
                    nanos = notFull.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notFull.signal(); // propagate to a non-interrupted thread
                    throw ie;
                }
            }
        }
        finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return true;
    }

    public boolean offer(E x) {
        if (x == null) throw new IllegalArgumentException();
        if (count.get() == maximumSize)
            return false;
        putLock.tryLock();
        int c = -1;
        try {
            if (count.get() < maximumSize) {
                insert(x);
                c = count.getAndIncrement();
                if (c+1 < maximumSize)
                    notFull.signal();
            }
        }
        finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return c >= 0;
    }


    public E take() throws InterruptedException {
        E x;
        int c = -1;
        takeLock.lockInterruptibly();
        try {
            try {
                while (count.get() == 0)
                    notEmpty.await();
            }
            catch (InterruptedException ie) {
                notEmpty.signal(); // propagate to a non-interrupted thread
                throw ie;
            }

            x = extract();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        }
        finally {
            takeLock.unlock();
        }
        if (c == maximumSize)
            signalNotFull();
        return x;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {

        E x = null;
        int c = -1;
        takeLock.lockInterruptibly();
        long nanos = unit.toNanos(timeout);
        try {
            for (;;) {
                if (count.get() > 0) {
                    x = extract();
                    c = count.getAndDecrement();
                    if (c > 1)
                        notEmpty.signal();
                    break;
                }
                if (nanos <= 0)
                    return null;
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                }
                catch (InterruptedException ie) {
                    notEmpty.signal(); // propagate to a non-interrupted thread
                    throw ie;
                }
            }
        }
        finally {
            takeLock.unlock();
        }
        if (c == maximumSize)
            signalNotFull();
        return x;
    }

    public E poll() {
        if (count.get() == 0)
            return null;
        E x = null;
        int c = -1;
        takeLock.tryLock();
        try {
            if (count.get() > 0) {
                x = extract();
                c = count.getAndDecrement();
                if (c > 1)
                    notEmpty.signal();
            }
        }
        finally {
            takeLock.unlock();
        }
        if (c == maximumSize)
            signalNotFull();
        return x;
    }


    public E peek() {
        if (count.get() == 0)
            return null;
        takeLock.tryLock();
        try {
            Node<E> first = head.next;
            if (first == null)
                return null;
            else
                return first.item;
        }
        finally {
            takeLock.unlock();
        }
    }

    public boolean remove(Object x) {
        if (x == null) return false;
        boolean removed = false;
        fullyLock();
        try {
            Node<E> trail = head;
            Node<E> p = head.next;
            while (p != null) {
                if (x.equals(p.item)) {
                    removed = true;
                    break;
                }
                trail = p;
                p = p.next;
            }
            if (removed) {
                p.item = null;
                trail.next = p.next;
                if (count.getAndDecrement() == maximumSize)
                    notFull.signalAll();
            }
        }
        finally {
            fullyUnlock();
        }
        return removed;
    }

    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        }
        finally {
            fullyUnlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance(
                                                             a.getClass().getComponentType(), size);

            int k = 0;
            for (Node p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            return a;
        }
        finally {
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            return super.toString();
        }
        finally {
            fullyUnlock();
        }
    }

    public Iterator<E> iterator() {
      return new Itr();
    }

    private class Itr implements Iterator<E> {
        Node<E> current;
        Node<E> lastRet;

        // for comodification checks
        Node<E> expectedHead;
        Node<E> expectedLast;
        int expectedCount;

        Itr() {
            fullyLock();
            try {
                expectedHead = head;
                current = head.next;
                expectedLast = last;
                expectedCount = count.get();
            }
            finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return current != null;
        }

        private void checkForModification() {
            if (expectedHead != head ||
                expectedLast != last ||
                expectedCount != count.get())
                throw new  ConcurrentModificationException();
        }

        public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                checkForModification();
                E x = current.item;
                lastRet = current;
                current = current.next;
                return x;
            }
            finally {
                fullyUnlock();
            }

        }

        public void remove() {
            if (lastRet == null)
                throw new IllegalStateException();
            fullyLock();
            try {
                checkForModification();
                Node<E> node = lastRet;
                lastRet = null;
                Node<E> trail = head;
                Node<E> p = head.next;
                while (p != null && p != node) {
                    trail = p;
                    p = p.next;
                }
                if (p == node) {
                    p.item = null;
                    trail.next = p.next;
                    int c = count.getAndDecrement();
                    expectedHead = head;
                    expectedLast = last;
                    expectedCount = c;
                    if (c == maximumSize)
                        notFull.signalAll();
                }
            }
            finally {
                fullyUnlock();
            }
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData The maximumSize is emitted (int), followed by all of
     * its elements (each an <tt>Object</tt>) in the proper order,
     * followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus maximumSize
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        }
        finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitute the Queue instance from a stream (that is,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in maximumSize, and any hidden stuff
        s.defaultReadObject();

        // Read in all elements and place in queue
        for (;;) {
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}

