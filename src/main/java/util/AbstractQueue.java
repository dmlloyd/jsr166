package java.util;

/**
 * AbstractQueue provides default implementations of add, remove, and
 * element based on offer, poll, and peek, respectively but that throw
 * exceptions instead of indicating failure via false or null returns.
 * The provided implementations all assume that the base implementation
 * does <em>not</em> allow null elements.
 */
public abstract class AbstractQueue<E> extends AbstractCollection<E> implements Queue<E> {

    public boolean add(E x) {
        if (offer(x))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }

    public E remove() {
        E x = poll();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    public E element() {
        E x = peek();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }

    public void clear() {
        while (poll() != null)
            ;
    }

}
