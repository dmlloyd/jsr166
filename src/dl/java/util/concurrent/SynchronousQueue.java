package java.util.concurrent;

import java.util.*;

/**
 * A Queue in which each put must wait for a take, and vice versa.
 * SynchronousQueues are similar to rendezvous channels used in CSP
 * and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must synch up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 **/
public class SynchronousQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /*
      This implementation divides actions into two cases for puts:

      * An arriving putter that does not already have a waiting taker
      creates a node holding item, and then waits for a taker to take it.
      * An arriving putter that does already have a waiting taker fills
      the slot node created by the taker, and notifies it to continue.

      And symmetrically, two for takes:

      * An arriving taker that does not already have a waiting putter
      creates an empty slot node, and then waits for a putter to fill it.
      * An arriving taker that does already have a waiting putter takes
      item from the node created by the putter, and notifies it to continue.

      This requires keeping two simple queues: waitingPuts and waitingTakes.

      When a put or take waiting for the actions of its counterpart
      aborts due to interruption or timeout, it marks the node
      it created as "CANCELLED", which causes its counterpart to retry
      the entire put or take sequence.
    */

    /**
     * Special marker used in queue nodes to indicate that
     * the thread waiting for a change in the node has timed out
     * or been interrupted.
     **/
    private static final Object CANCELLED = new Object();

    /*
     * Note that all fields are transient final, so there is
     * no explicit serialization code.
     */

    private transient final WaitQueue waitingPuts = new WaitQueue();
    private transient final WaitQueue waitingTakes = new WaitQueue();
    private transient final ReentrantLock qlock = new ReentrantLock();

    /**
     * Nodes each maintain an item and handle waits and signals for
     * getting and setting it.
     */
    private static class Node {
        final ReentrantLock lock = new ReentrantLock();
        Condition done;
        Object item;
        Node next;
        Node(Object x) { item = x; }

        /**
         * Fill in the slot created by the taker and signal taker to
         * continue.
         */
        boolean set(Object x) {
            lock.lock();
            try {
                if (item != CANCELLED) {
                    item = x;
                    if (done != null)
                        done.signal();
                    return true;
                }
                else // taker has cancelled
                    return false;
            }
            finally {
                lock.unlock();
            }
        }

        /**
         * Remove item from slot created by putter and signal putter
         * to continue.
         */
        Object get() {
            lock.lock();
            try {
                Object x = item;
                if (x != CANCELLED) {
                    item = null;
                    next = null;
                    if (done != null)
                        done.signal();
                    return x;
                }
                else
                    return null;
            }
            finally {
                lock.unlock();
            }
        }


        /**
         * Wait for a taker to take item placed by putter, or time out.
         */
        boolean waitForTake(boolean timed, long nanos) throws InterruptedException {
            lock.lock();
            try {
                for (;;) {
                    if (item == null)
                        return true;
                    if (done == null)
                        done = lock.newCondition();
                    if (timed) {
                        if (nanos <= 0) {
                            item = CANCELLED;
                            return false;
                        }
                        nanos = done.awaitNanos(nanos);
                    }
                    else
                        done.await();
                }
            }
            catch (InterruptedException ie) {
                // If taken, return normally but set interrupt status
                if (item == null) {
                    Thread.currentThread().interrupt();
                    return true;
                }
                else {
                    item = CANCELLED;
                    throw ie;
                }
            }
            finally {
                lock.unlock();
            }
        }


        /**
         * Wait for a putter to put item placed by taker, or time out.
         */
        Object waitForPut(boolean timed, long nanos) throws InterruptedException {
            lock.lock();
            try {
                for (;;) {
                    Object x = item;
                    if (x != null) {
                        item = null;
                        next = null;
                        return x;
                    }
                    if (done == null)
                        done = lock.newCondition();
                    if (timed) {
                        if (nanos <= 0) {
                            item = CANCELLED;
                            return null;
                        }
                        nanos = done.awaitNanos(nanos);
                    }
                    else
                        done.await();
                }
            }
            catch(InterruptedException ie) {
                Object x = item;
                if (x != null) {
                    item = null;
                    next = null;
                    Thread.currentThread().interrupt();
                    return x;
                }
                else {
                    item = CANCELLED;
                    throw ie;
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Simple FIFO queue class to hold waiting puts/takes.
     **/
    private static class WaitQueue<E> {
        Node head;
        Node last;

        Node enq(Object x) {
            Node p = new Node(x);
            if (last == null)
                last = head = p;
            else
                last = last.next = p;
            return p;
        }

        Node deq() {
            Node p = head;
            if (p != null && (head = p.next) == null)
                last = null;
            return p;
        }
    }

    /**
     * Main put algorithm, used by put, timed offer
     */
    private boolean doPut(E x, boolean timed, long nanos) throws InterruptedException {
        if (x == null) throw new IllegalArgumentException();
        for (;;) {
            Node node;
            boolean mustWait;

            qlock.lockInterruptibly();
            try {
                node = waitingTakes.deq();
                if ( (mustWait = (node == null)) )
                    node = waitingPuts.enq(x);
            }
            finally {
                qlock.unlock();
            }

            if (mustWait)
                return node.waitForTake(timed, nanos);

            else if (node.set(x))
                return true;

            // else taker cancelled, so retry
        }
    }

    /**
     * Main take algorithm, used by take, timed poll
     */
    private E doTake(boolean timed, long nanos) throws InterruptedException {
        for (;;) {
            Node node;
            boolean mustWait;

            qlock.lockInterruptibly();
            try {
                node = waitingPuts.deq();
                if ( (mustWait = (node == null)) )
                    node = waitingTakes.enq(null);
            }
            finally {
                qlock.unlock();
            }

            if (mustWait)
                return (E)node.waitForPut(timed, nanos);

            else {
                E x = (E)node.get();
                if (x != null)
                    return x;
                // else cancelled, so retry
            }
        }
    }

    public SynchronousQueue() {}

    public boolean isEmpty() {
        return true;
    }

    public int size() {
        return 0;
    }

    public int maximumSize() {
        return 0;
    }

    public E peek() {
        return null;
    }


    public void put(E x) throws InterruptedException {
        doPut(x, false, 0);
    }

    public boolean offer(E x, long timeout, TimeUnit unit) throws InterruptedException {
        return doPut(x, true, unit.toNanos(timeout));
    }



    public E take() throws InterruptedException {
        return doTake(false, 0);
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return doTake(true, unit.toNanos(timeout));
    }

    // Untimed nonblocking versions

    public boolean offer(E x) {
        if (x == null) throw new IllegalArgumentException();

        for (;;) {
            qlock.lock();
            Node node;
            try {
                node = waitingTakes.deq();
            }
            finally {
                qlock.unlock();
            }
            if (node == null)
                return false;

            else if (node.set(x))
                return true;
            // else retry
        }
    }

    public E poll() {
        for (;;) {
            Node node;
            qlock.lock();
            try {
                node = waitingPuts.deq();
            }
            finally {
                qlock.unlock();
            }
            if (node == null)
                return null;

            else {
                Object x = node.get();
                if (x != null)
                    return (E)x;
                // else retry
            }
        }
    }

    public boolean remove(Object x) {
        return false;
    }

    static class EmptyIterator<E> implements Iterator {
        public boolean hasNext() {
            return false;
        }
        public E next() {
            throw new NoSuchElementException();
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public Iterator<E> iterator() {
        return new EmptyIterator();
    }


    public E[] toArray() {
        return new E[0];
    }

    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }


}
