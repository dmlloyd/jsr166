/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;

/*
 * @test
 * @bug 6805775 6815766
 * @summary Check weak consistency of concurrent queue iterators
 */

@SuppressWarnings({"unchecked", "rawtypes"})
public class IteratorWeakConsistency {
    final Random rnd = new Random();

    void test(String[] args) throws Throwable {
        test(new LinkedBlockingQueue());
        test(new LinkedBlockingQueue(20));
        test(new LinkedBlockingDeque());
        test(new LinkedBlockingDeque(20));
        test(new ConcurrentLinkedDeque());
        test(new ConcurrentLinkedQueue());
        test(new LinkedTransferQueue());
        test(new ArrayBlockingQueue(20));
    }

    void checkExhausted(Iterator it) {
        if (rnd.nextBoolean()) {
            check(!it.hasNext());
        }
        if (rnd.nextBoolean())
            try { it.next(); fail("should throw"); }
            catch (NoSuchElementException success) {}
    }

    void checkRemoveThrowsISE(Iterator it) {
        if (rnd.nextBoolean()) {
            try { it.remove(); fail("should throw"); }
            catch (IllegalStateException success) {}
        }
    }

    void checkRemoveHasNoEffect(Iterator it, Collection c) {
        if (rnd.nextBoolean()) {
            int size = c.size();
            it.remove(); // no effect
            equal(c.size(), size);
            checkRemoveThrowsISE(it);
        }
    }

    void test(Queue q) {
        //----------------------------------------------------------------
        // Check iterators on an empty q
        //----------------------------------------------------------------
        try {
            for (int i = 0; i < 4; i++) {
                Iterator it = q.iterator();
                if (rnd.nextBoolean()) checkExhausted(it);
                checkRemoveThrowsISE(it);
            }
        } catch (Throwable t) { unexpected(t); }

        // TODO: make this more general
        try {
            for (int i = 0; i < 10; i++)
                q.add(i);
            Iterator it = q.iterator();
            q.poll();
            q.poll();
            q.poll();
            q.remove(7);
            List list = new ArrayList();
            while (it.hasNext())
                list.add(it.next());
            equal(list, Arrays.asList(0, 3, 4, 5, 6, 8, 9));
            check(! list.contains(null));
            System.err.printf("%s: %s%n",
                              q.getClass().getSimpleName(),
                              list);
        } catch (Throwable t) { unexpected(t); }

        try {
            q.clear();
            q.add(1);
            q.add(2);
            q.add(3);
            q.add(4);
            Iterator it = q.iterator();
            it.next();
            q.remove(2);
            q.remove(1);
            q.remove(3);
            boolean found4 = false;
            while (it.hasNext()) {
                found4 |= it.next().equals(4);
            }
            check(found4);
        } catch (Throwable t) { unexpected(t); }

        try {
            q.clear();
            Object x = new Object();
            for (int i = 0; i < 20; i++)
                q.add(x);
            equal(20, q.size());
            Iterator it1 = q.iterator();
            Iterator it2 = q.iterator();
            try { it1.remove(); fail(); }
            catch (IllegalStateException success) {}
            try { it2.remove(); fail(); }
            catch (IllegalStateException success) {}

            check(it1.next() == x);
            check(it2.hasNext());
            check(it2.next() == x);
            it1.remove();
            it2.remove();
            equal(19, q.size());
            try { it1.remove(); fail(); }
            catch (IllegalStateException success) {}
            try { it2.remove(); fail(); }
            catch (IllegalStateException success) {}
            equal(19, q.size());

            it1.next();
            check(it2.hasNext());
            it2.next();
            it2.remove();
            it1.remove();
            equal(18, q.size());

            it1.next();
            it2.next();
            check(q.remove() == x);
            equal(17, q.size());
            it1.remove();
            it2.remove();
            equal(17, q.size());
        } catch (Throwable t) { unexpected(t); }

        //----------------------------------------------------------------
        // Check "interior" removal of alternating elements
        //----------------------------------------------------------------
        try {
            q.clear();
            final int remainingCapacity = (q instanceof BlockingQueue) ?
                ((BlockingQueue)q).remainingCapacity() :
                Integer.MAX_VALUE;
            final int capacity = Math.min(20, remainingCapacity);
            List<Iterator> its = new ArrayList<Iterator>();
            // Move to "middle"
            for (int i = 0; i < capacity/2; i++) {
                check(q.add(i));
                equal(q.poll(), i);
            }
            for (int i = 0; i < capacity; i++)
                check(q.add(i));
            for (int i = 0; i < capacity; i++) {
                Iterator it = q.iterator();
                its.add(it);
                for (int j = 0; j < i; j++)
                    equal(j, it.next());
            }

            // Remove all even elements, in either direction using
            // q.remove(), or iterator.remove()
            switch (rnd.nextInt(3)) {
            case 0:
                for (int i = 0; i < capacity; i+=2)
                    check(q.remove(i));
                break;
            case 1:
                for (int i = capacity - 2; i >= 0; i-=2)
                    check(q.remove(i));
                break;
            case 2:
                Iterator it = q.iterator();
                while (it.hasNext()) {
                    int i = (Integer) it.next();
                    if ((i & 1) == 0)
                        it.remove();
                }
                break;
            default: throw new Error();
            }

            for (int i = 0; i < capacity; i++) {
                Iterator it = its.get(i);
                boolean even = ((i & 1) == 0);
                if (even) {
                    if (rnd.nextBoolean()) check(it.hasNext());
                    equal(i, it.next());
                    for (int j = i+1; j < capacity; j += 2)
                        equal(j, it.next());
                    check(!it.hasNext());
                } else { /* odd */
                    if (rnd.nextBoolean()) check(it.hasNext());
                    checkRemoveHasNoEffect(it, q);
                    equal(i, it.next());
                    for (int j = i+2; j < capacity; j += 2)
                        equal(j, it.next());
                    check(!it.hasNext());
                }
            }

            // q only contains odd elements
            for (int i = 0; i < capacity; i++)
                check(q.contains(i) ^ ((i & 1) == 0));

        } catch (Throwable t) { unexpected(t); }

    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new IteratorWeakConsistency().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.err.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
