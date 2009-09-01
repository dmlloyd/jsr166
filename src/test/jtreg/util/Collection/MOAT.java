/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     6207984 6272521 6192552 6269713 6197726 6260652 5073546 4137464
 *          4155650 4216399 4294891 6282555 6318622 6355327 6383475 6420753
 *          6431845 4802633 6570566 6570575 6570631 6570924 6691185 6691215
 * @summary Run many tests on many Collection and Map implementations
 * @author  Martin Buchholz
 */

/* Mother Of All (Collection) Tests
 *
 * Testing of collection classes is often spotty, because many tests
 * need to be performed on many implementations, but the onus on
 * writing the tests falls on the engineer introducing the new
 * implementation.
 *
 * The idea of this mega-test is that:
 *
 * An engineer adding a new collection implementation could simply add
 * their new implementation to a list of implementations in this
 * test's main method.  Any general purpose Collection<Integer> or
 * Map<Integer,Integer> class is appropriate.
 *
 * An engineer fixing a regression could add their regression test here and
 * simultaneously test all other implementations.
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.Collections.*;

public class MOAT {
    public static void realMain(String[] args) {

        testCollection(new LinkedHashSet<Integer>());
        testCollection(new HashSet<Integer>());
        testCollection(new Vector<Integer>());
        testCollection(new Vector<Integer>().subList(0,0));
        testCollection(new ArrayDeque<Integer>());
        testCollection(new ArrayList<Integer>());
        testCollection(new ArrayList<Integer>().subList(0,0));
        testCollection(new LinkedList<Integer>());
        testCollection(new LinkedList<Integer>().subList(0,0));
        testCollection(new TreeSet<Integer>());

        testCollection(new CopyOnWriteArrayList<Integer>());
        testCollection(new CopyOnWriteArrayList<Integer>().subList(0,0));
        testCollection(new CopyOnWriteArraySet<Integer>());
        testCollection(new PriorityQueue<Integer>());
        testCollection(new PriorityBlockingQueue<Integer>());
        testCollection(new ArrayBlockingQueue<Integer>(20));
        testCollection(new LinkedBlockingQueue<Integer>(20));
        testCollection(new LinkedBlockingDeque<Integer>(20));
        testCollection(new ConcurrentLinkedQueue<Integer>());
        testCollection(new LinkedTransferQueue<Integer>());
        testCollection(new ConcurrentSkipListSet<Integer>());
        testCollection(Arrays.asList(new Integer(42)));
        testCollection(Arrays.asList(1,2,3));
        testCollection(nCopies(25,1));
        testImmutableList(nCopies(25,1));
        testImmutableList(unmodifiableList(Arrays.asList(1,2,3)));

        testMap(new HashMap<Integer,Integer>());
        testMap(new LinkedHashMap<Integer,Integer>());
        testMap(new WeakHashMap<Integer,Integer>());
        testMap(new IdentityHashMap<Integer,Integer>());
        testMap(new TreeMap<Integer,Integer>());
        testMap(new Hashtable<Integer,Integer>());
        testMap(new ConcurrentHashMap<Integer,Integer>(10, 0.5f));
        testMap(new ConcurrentSkipListMap<Integer,Integer>());

        // Empty collections
        final List<Integer> emptyArray = Arrays.asList(new Integer[]{});
        testCollection(emptyArray);
        testEmptyList(emptyArray);
        THROWS(IndexOutOfBoundsException.class,
               new Fun(){void f(){ emptyArray.set(0,1); }});
        THROWS(UnsupportedOperationException.class,
               new Fun(){void f(){ emptyArray.add(0,1); }});

        List<Integer> noOne = nCopies(0,1);
        testCollection(noOne);
        testEmptyList(noOne);
        testImmutableList(noOne);

        Set<Integer> emptySet = emptySet();
        testCollection(emptySet);
        testEmptySet(emptySet);
        testEmptySet(EMPTY_SET);
        testImmutableSet(emptySet);

        List<Integer> emptyList = emptyList();
        testCollection(emptyList);
        testEmptyList(emptyList);
        testEmptyList(EMPTY_LIST);
        testImmutableList(emptyList);

        Map<Integer,Integer> emptyMap = emptyMap();
        testMap(emptyMap);
        testEmptyMap(emptyMap);
        testEmptyMap(EMPTY_MAP);
        testImmutableMap(emptyMap);

        // Singleton collections
        Set<Integer> singletonSet = singleton(1);
        equal(singletonSet.size(), 1);
        testCollection(singletonSet);
        testImmutableSet(singletonSet);

        List<Integer> singletonList = singletonList(1);
        equal(singletonList.size(), 1);
        testCollection(singletonList);
        testImmutableList(singletonList);
        testImmutableList(singletonList.subList(0,1));
        testImmutableList(singletonList.subList(0,1).subList(0,1));
        testEmptyList(singletonList.subList(0,0));
        testEmptyList(singletonList.subList(0,0).subList(0,0));

        Map<Integer,Integer> singletonMap = singletonMap(1,2);
        equal(singletonMap.size(), 1);
        testMap(singletonMap);
        testImmutableMap(singletonMap);
    }

    private static void checkContainsSelf(Collection<Integer> c) {
        check(c.containsAll(c));
        check(c.containsAll(Arrays.asList(c.toArray())));
        check(c.containsAll(Arrays.asList(c.toArray(new Integer[0]))));
    }

    private static void checkContainsEmpty(Collection<Integer> c) {
        check(c.containsAll(new ArrayList<Integer>()));
    }

    private static <T> void testEmptyCollection(Collection<T> c) {
        check(c.isEmpty());
        equal(c.size(), 0);
        equal(c.toString(),"[]");
        equal(c.toArray().length, 0);
        equal(c.toArray(new Object[0]).length, 0);
        check(c.toArray(new Object[]{42})[0] == null);

        Object[] a = new Object[1]; a[0] = Boolean.TRUE;
        equal(c.toArray(a), a);
        equal(a[0], null);
        testEmptyIterator(c.iterator());
    }

    static <T> void testEmptyIterator(final Iterator<T> it) {
        if (rnd.nextBoolean())
            check(! it.hasNext());

        THROWS(NoSuchElementException.class,
               new Fun(){void f(){ it.next(); }});

        try { it.remove(); }
        catch (IllegalStateException _) { pass(); }
        catch (UnsupportedOperationException _) { pass(); }
        catch (Throwable t) { unexpected(t); }

        if (rnd.nextBoolean())
            check(! it.hasNext());
    }

    private static void testEmptyList(List<?> c) {
        testEmptyCollection(c);
        equal(c.hashCode(), 1);
        equal2(c, Collections.<Integer>emptyList());
    }

    private static <T> void testEmptySet(Set<T> c) {
        testEmptyCollection(c);
        equal(c.hashCode(), 0);
        equal2(c, Collections.<Integer>emptySet());
        if (c instanceof NavigableSet<?>)
            testEmptyIterator(((NavigableSet<T>)c).descendingIterator());
    }

    private static void testImmutableCollection(final Collection<Integer> c) {
        THROWS(UnsupportedOperationException.class,
               new Fun(){void f(){ c.add(99); }},
               new Fun(){void f(){ c.addAll(singleton(99)); }});
        if (! c.isEmpty()) {
            final Integer first = c.iterator().next();
            THROWS(UnsupportedOperationException.class,
                   new Fun(){void f(){ c.clear(); }},
                   new Fun(){void f(){ c.remove(first); }},
                   new Fun(){void f(){ c.removeAll(singleton(first)); }},
                   new Fun(){void f(){ c.retainAll(emptyList()); }}
                   );
        }
    }

    private static void testImmutableSet(final Set<Integer> c) {
        testImmutableCollection(c);
    }

    private static void testImmutableList(final List<Integer> c) {
        testList(c);
        testImmutableCollection(c);
        THROWS(UnsupportedOperationException.class,
               new Fun(){void f(){ c.set(0,42); }},
               new Fun(){void f(){ c.add(0,42); }},
               new Fun(){void f(){ c.addAll(0,singleton(86)); }});
        if (! c.isEmpty())
            THROWS(UnsupportedOperationException.class,
                   new Fun(){void f(){
                           Iterator<Integer> it = c.iterator();
                           it.next(); it.remove();}},
                   new Fun(){void f(){
                           ListIterator<Integer> it = c.listIterator();
                           it.next(); it.remove();}});
    }

    private static void clear(Collection<Integer> c) {
        try { c.clear(); }
        catch (Throwable t) { unexpected(t); }
        testEmptyCollection(c);
    }

    private static <K,V> void testEmptyMap(final Map<K,V> m) {
        check(m.isEmpty());
        equal(m.size(), 0);
        equal(m.toString(),"{}");
        testEmptySet(m.keySet());
        testEmptySet(m.entrySet());
        testEmptyCollection(m.values());

        try { check(! m.containsValue(null)); }
        catch (NullPointerException _) { /* OK */ }
        try { check(! m.containsKey(null)); }
        catch (NullPointerException _) { /* OK */ }
        check(! m.containsValue(1));
        check(! m.containsKey(1));
    }

    private static void testImmutableMap(final Map<Integer,Integer> m) {
        THROWS(UnsupportedOperationException.class,
               new Fun(){void f(){ m.put(1,1); }},
               new Fun(){void f(){ m.putAll(singletonMap(1,1)); }});
        if (! m.isEmpty()) {
            final Integer first = m.keySet().iterator().next();
            THROWS(UnsupportedOperationException.class,
                   new Fun(){void f(){ m.remove(first); }},
                   new Fun(){void f(){ m.clear(); }});
            final Map.Entry<Integer,Integer> me
                = m.entrySet().iterator().next();
            Integer key = me.getKey();
            Integer val = me.getValue();
            THROWS(UnsupportedOperationException.class,
                   new Fun(){void f(){ me.setValue(3); }});
            equal(key, me.getKey());
            equal(val, me.getValue());
        }
        testImmutableSet(m.keySet());
        testImmutableCollection(m.values());
        //testImmutableSet(m.entrySet());
    }

    private static void clear(Map<?,?> m) {
        try { m.clear(); }
        catch (Throwable t) { unexpected(t); }
        testEmptyMap(m);
    }

    private static void oneElement(Collection<Integer> c) {
        clear(c);
        try {
            check(c.add(-42));
            equal(c.toString(), "[-42]");
            if (c instanceof Set) check(! c.add(-42));
        } catch (Throwable t) { unexpected(t); }
        check(! c.isEmpty()); check(c.size() == 1);
    }

    private static boolean supportsAdd(Collection<Integer> c) {
        try { check(c.add(778347983)); }
        catch (UnsupportedOperationException t) { return false; }
        catch (Throwable t) { unexpected(t); }

        try {
            check(c.contains(778347983));
            check(c.remove(778347983));
        } catch (Throwable t) { unexpected(t); }
        return true;
    }

    private static boolean supportsRemove(Collection<Integer> c) {
        try { check(! c.remove(19134032)); }
        catch (UnsupportedOperationException t) { return false; }
        catch (Throwable t) { unexpected(t); }
        return true;
    }

    private static void checkFunctionalInvariants(Collection<Integer> c) {
        try {
            checkContainsSelf(c);
            checkContainsEmpty(c);
            check(c.size() != 0 ^ c.isEmpty());

            {
                int size = 0;
                for (Integer i : c) size++;
                check(c.size() == size);
            }

            check(c.toArray().length == c.size());
            check(c.toArray().getClass() == Object[].class
                  ||
                  // !!!!
                  // 6260652: (coll) Arrays.asList(x).toArray().getClass()
                  // should be Object[].class
                  (c.getClass().getName().equals("java.util.Arrays$ArrayList"))
                  );
            for (int size : new int[]{0,1,c.size(), c.size()+1}) {
                Integer[] a = c.toArray(new Integer[size]);
                check((size > c.size()) || a.length == c.size());
                int i = 0; for (Integer j : c) check(a[i++] == j);
                check((size <= c.size()) || (a[c.size()] == null));
                check(a.getClass() == Integer[].class);
            }

            check(c.equals(c));
            if (c instanceof Serializable) {
                //System.out.printf("Serializing %s%n", c.getClass().getName());
                try {
                    Object clone = serialClone(c);
                    equal(c instanceof Serializable,
                          clone instanceof Serializable);
                    equal(c instanceof RandomAccess,
                          clone instanceof RandomAccess);
                    if ((c instanceof List) || (c instanceof Set))
                        equal(c, clone);
                    else
                        equal(new HashSet<Integer>(c),
                              new HashSet<Integer>(serialClone(c)));
                } catch (Error xxx) {
                    if (! (xxx.getCause() instanceof NotSerializableException))
                        throw xxx;
                }
            }
        }
        catch (Throwable t) { unexpected(t); }
    }

    //----------------------------------------------------------------
    // If add(null) succeeds, contains(null) & remove(null) should succeed
    //----------------------------------------------------------------
    private static void testNullElement(Collection<Integer> c) {
        // !!!! 5018849: (coll) TreeSet.contains(null) does not agree with Javadoc
        if (c instanceof TreeSet) return;

        try {
            check(c.add(null));
            if (c.size() == 1)
                equal(c.toString(), "[null]");
            try {
                checkFunctionalInvariants(c);
                check(c.contains(null));
                check(c.remove(null));
            }
            catch (Throwable t) { unexpected(t); }
        }
        catch (NullPointerException e) { /* OK */ }
        catch (Throwable t) { unexpected(t); }
    }


    //----------------------------------------------------------------
    // If add("x") succeeds, contains("x") & remove("x") should succeed
    //----------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private static void testStringElement(Collection<Integer> c) {
        Collection x = (Collection)c; // Make type-unsafe
        try {
            check(x.add("x"));
            try {
                check(x.contains("x"));
                check(x.remove("x"));
            } catch (Throwable t) { unexpected(t); }
        }
        catch (ClassCastException e) { /* OK */ }
        catch (Throwable t) { unexpected(t); }
    }

    private static void testConcurrentCollection(Collection<Integer> c) {
        try {
            c.add(1);
            Iterator<Integer> it = c.iterator();
            check(it.hasNext());
            clear(c);
            check(it.next() instanceof Integer); // No CME
            check(c.isEmpty());
        }
        catch (Throwable t) { unexpected(t); }
    }

    private static void testQueue(Queue<Integer> q) {
        q.clear();
        for (int i = 0; i < 5; i++)
            q.add(i);
        equal(q.size(), 5);
        checkFunctionalInvariants(q);
        q.poll();
        equal(q.size(), 4);
        checkFunctionalInvariants(q);
        if ((q instanceof LinkedBlockingQueue) ||
            (q instanceof LinkedBlockingDeque) ||
            (q instanceof ConcurrentLinkedQueue)) {
            testQueueIteratorRemove(q);
        }
    }

    private static void testQueueIteratorRemove(Queue<Integer> q) {
        System.err.printf("testQueueIteratorRemove %s%n",
                          q.getClass().getSimpleName());
        q.clear();
        for (int i = 0; i < 5; i++)
            q.add(i);
        Iterator<Integer> it = q.iterator();
        check(it.hasNext());
        for (int i = 3; i >= 0; i--)
            q.remove(i);
        equal(it.next(), 0);
        equal(it.next(), 4);

        q.clear();
        for (int i = 0; i < 5; i++)
            q.add(i);
        it = q.iterator();
        equal(it.next(), 0);
        check(it.hasNext());
        for (int i = 1; i < 4; i++)
            q.remove(i);
        equal(it.next(), 1);
        equal(it.next(), 4);
    }

    private static void testList(final List<Integer> l) {
        //----------------------------------------------------------------
        // 4802633: (coll) AbstractList.addAll(-1,emptyCollection)
        // doesn't throw IndexOutOfBoundsException
        //----------------------------------------------------------------
        try {
            l.addAll(-1, Collections.<Integer>emptyList());
            fail("Expected IndexOutOfBoundsException not thrown");
        }
        catch (UnsupportedOperationException _) {/* OK */}
        catch (IndexOutOfBoundsException _) {/* OK */}
        catch (Throwable t) { unexpected(t); }

//      equal(l instanceof Serializable,
//            l.subList(0,0) instanceof Serializable);
        if (l.subList(0,0) instanceof Serializable)
            check(l instanceof Serializable);

        equal(l instanceof RandomAccess,
              l.subList(0,0) instanceof RandomAccess);
    }

    private static void testCollection(Collection<Integer> c) {
        try { testCollection1(c); }
        catch (Throwable t) { unexpected(t); }
    }

    private static void testCollection1(Collection<Integer> c) {

        System.out.println("\n==> " + c.getClass().getName());

        checkFunctionalInvariants(c);

        if (! supportsAdd(c)) return;
        //System.out.println("add() supported");

        if (c instanceof NavigableSet) {
            System.out.println("NavigableSet tests...");

            NavigableSet<Integer> ns = (NavigableSet<Integer>)c;
            testNavigableSet(ns);
            testNavigableSet(ns.headSet(6, false));
            testNavigableSet(ns.headSet(5, true));
            testNavigableSet(ns.tailSet(0, false));
            testNavigableSet(ns.tailSet(1, true));
            testNavigableSet(ns.subSet(0, false, 5, true));
            testNavigableSet(ns.subSet(1, true, 6, false));
        }

        if (c instanceof Queue)
            testQueue((Queue<Integer>)c);

        if (c instanceof List)
            testList((List<Integer>)c);

        check(supportsRemove(c));

        try {
            oneElement(c);
            checkFunctionalInvariants(c);
        }
        catch (Throwable t) { unexpected(t); }

        clear(c);      testNullElement(c);
        oneElement(c); testNullElement(c);

        clear(c);      testStringElement(c);
        oneElement(c); testStringElement(c);

        if (c.getClass().getName().matches(".*concurrent.*"))
            testConcurrentCollection(c);

        //----------------------------------------------------------------
        // The "all" operations should throw NPE when passed null
        //----------------------------------------------------------------
        {
            oneElement(c);
            try {
                c.removeAll(null);
                fail("Expected NullPointerException");
            }
            catch (NullPointerException e) { pass(); }
            catch (Throwable t) { unexpected(t); }

            oneElement(c);
            try {
                c.retainAll(null);
                fail("Expected NullPointerException");
            }
            catch (NullPointerException e) { pass(); }
            catch (Throwable t) { unexpected(t); }

            oneElement(c);
            try {
                c.addAll(null);
                fail("Expected NullPointerException");
            }
            catch (NullPointerException e) { pass(); }
            catch (Throwable t) { unexpected(t); }

            oneElement(c);
            try {
                c.containsAll(null);
                fail("Expected NullPointerException");
            }
            catch (NullPointerException e) { pass(); }
            catch (Throwable t) { unexpected(t); }
        }
    }

    //----------------------------------------------------------------
    // Map
    //----------------------------------------------------------------
    private static void checkFunctionalInvariants(Map<Integer,Integer> m) {
        check(m.keySet().size() == m.entrySet().size());
        check(m.keySet().size() == m.size());
        checkFunctionalInvariants(m.keySet());
        checkFunctionalInvariants(m.values());
        check(m.size() != 0 ^ m.isEmpty());
    }

    private static void testMap(Map<Integer,Integer> m) {
        System.out.println("\n==> " + m.getClass().getName());

        if (m instanceof ConcurrentMap)
            testConcurrentMap((ConcurrentMap<Integer,Integer>) m);

        if (m instanceof NavigableMap) {
            System.out.println("NavigableMap tests...");

            NavigableMap<Integer,Integer> nm =
                (NavigableMap<Integer,Integer>) m;
            testNavigableMapRemovers(nm);
            testNavigableMap(nm);
            testNavigableMap(nm.headMap(6, false));
            testNavigableMap(nm.headMap(5, true));
            testNavigableMap(nm.tailMap(0, false));
            testNavigableMap(nm.tailMap(1, true));
            testNavigableMap(nm.subMap(1, true, 6, false));
            testNavigableMap(nm.subMap(0, false, 5, true));
        }

        checkFunctionalInvariants(m);

        if (supportsClear(m)) {
            try { clear(m); }
            catch (Throwable t) { unexpected(t); }
        }

        if (supportsPut(m)) {
            try {
                check(m.put(3333, 77777) == null);
                check(m.put(9134, 74982) == null);
                check(m.get(9134) == 74982);
                check(m.put(9134, 1382) == 74982);
                check(m.get(9134) == 1382);
                check(m.size() == 2);
                checkFunctionalInvariants(m);
                checkNPEConsistency(m);
            }
            catch (Throwable t) { unexpected(t); }
        }
    }

    private static boolean supportsPut(Map<Integer,Integer> m) {
        // We're asking for .equals(...) semantics
        if (m instanceof IdentityHashMap) return false;

        try { check(m.put(778347983,12735) == null); }
        catch (UnsupportedOperationException t) { return false; }
        catch (Throwable t) { unexpected(t); }

        try {
            check(m.containsKey(778347983));
            check(m.remove(778347983) != null);
        } catch (Throwable t) { unexpected(t); }
        return true;
    }

    private static boolean supportsClear(Map<?,?> m) {
        try { m.clear(); }
        catch (UnsupportedOperationException t) { return false; }
        catch (Throwable t) { unexpected(t); }
        return true;
    }

    //----------------------------------------------------------------
    // ConcurrentMap
    //----------------------------------------------------------------
    private static void testConcurrentMap(ConcurrentMap<Integer,Integer> m) {
        System.out.println("ConcurrentMap tests...");

        try {
            clear(m);

            check(m.putIfAbsent(18357,7346) == null);
            check(m.containsKey(18357));
            check(m.putIfAbsent(18357,8263) == 7346);
            try { m.putIfAbsent(18357,null); fail("NPE"); }
            catch (NullPointerException t) { }
            check(m.containsKey(18357));

            check(! m.replace(18357,8888,7777));
            check(m.containsKey(18357));
            try { m.replace(18357,null,7777); fail("NPE"); }
            catch (NullPointerException t) { }
            check(m.containsKey(18357));
            check(m.get(18357) == 7346);
            check(m.replace(18357,7346,5555));
            check(m.replace(18357,5555,7346));
            check(m.get(18357) == 7346);

            check(m.replace(92347,7834) == null);
            try { m.replace(18357,null); fail("NPE"); }
            catch (NullPointerException t) { }
            check(m.replace(18357,7346) == 7346);
            check(m.replace(18357,5555) == 7346);
            check(m.get(18357) == 5555);
            check(m.replace(18357,7346) == 5555);
            check(m.get(18357) == 7346);

            check(! m.remove(18357,9999));
            check(m.get(18357) == 7346);
            check(m.containsKey(18357));
            check(! m.remove(18357,null)); // 6272521
            check(m.get(18357) == 7346);
            check(m.remove(18357,7346));
            check(m.get(18357) == null);
            check(! m.containsKey(18357));
            check(m.isEmpty());

            m.putIfAbsent(1,2);
            check(m.size() == 1);
            check(! m.remove(1,null));
            check(! m.remove(1,null));
            check(! m.remove(1,1));
            check(m.remove(1,2));
            check(m.isEmpty());

            testEmptyMap(m);
        }
        catch (Throwable t) { unexpected(t); }
    }

    private static void throwsConsistently(Class<? extends Throwable> k,
                                           Iterable<Fun> fs) {
        List<Class<? extends Throwable>> threw
            = new ArrayList<Class<? extends Throwable>>();
        for (Fun f : fs)
            try { f.f(); threw.add(null); }
            catch (Throwable t) {
                check(k.isAssignableFrom(t.getClass()));
                threw.add(t.getClass());
            }
        if (new HashSet<Object>(threw).size() != 1)
            fail(threw.toString());
    }

    private static <T> void checkNPEConsistency(final Map<T,Integer> m) {
        m.clear();
        final ConcurrentMap<T,Integer> cm = (m instanceof ConcurrentMap)
            ? (ConcurrentMap<T,Integer>) m
            : null;
        List<Fun> fs = new ArrayList<Fun>();
        fs.add(new Fun(){void f(){ check(! m.containsKey(null));}});
        fs.add(new Fun(){void f(){ equal(m.remove(null), null);}});
        fs.add(new Fun(){void f(){ equal(m.get(null), null);}});
        if (cm != null) {
            fs.add(new Fun(){void f(){ check(! cm.remove(null,null));}});}
        throwsConsistently(NullPointerException.class, fs);

        fs.clear();
        final Map<T,Integer> sm = singletonMap(null,1);
        fs.add(new Fun(){void f(){ equal(m.put(null,1), null); m.clear();}});
        fs.add(new Fun(){void f(){ m.putAll(sm); m.clear();}});
        if (cm != null) {
            fs.add(new Fun(){void f(){ check(! cm.remove(null,null));}});
            fs.add(new Fun(){void f(){ equal(cm.putIfAbsent(null,1), 1);}});
            fs.add(new Fun(){void f(){ equal(cm.replace(null,1), null);}});
            fs.add(new Fun(){void f(){ equal(cm.replace(null,1, 1), 1);}});
        }
        throwsConsistently(NullPointerException.class, fs);
    }

    //----------------------------------------------------------------
    // NavigableMap
    //----------------------------------------------------------------
    private static void
        checkNavigableMapKeys(NavigableMap<Integer,Integer> m,
                              Integer i,
                              Integer lower,
                              Integer floor,
                              Integer ceiling,
                              Integer higher) {
        equal(m.lowerKey(i),   lower);
        equal(m.floorKey(i),   floor);
        equal(m.ceilingKey(i), ceiling);
        equal(m.higherKey(i),  higher);
    }

    private static void
        checkNavigableSetKeys(NavigableSet<Integer> m,
                              Integer i,
                              Integer lower,
                              Integer floor,
                              Integer ceiling,
                              Integer higher) {
        equal(m.lower(i),   lower);
        equal(m.floor(i),   floor);
        equal(m.ceiling(i), ceiling);
        equal(m.higher(i),  higher);
    }

    static final Random rnd = new Random();
    static void equalNext(final Iterator<?> it, Object expected) {
        if (rnd.nextBoolean())
            check(it.hasNext());
        equal(it.next(), expected);
    }

    static void equalMaps(Map m1, Map m2) {
        equal(m1, m2);
        equal(m2, m1);
        equal(m1.size(), m2.size());
        equal(m1.isEmpty(), m2.isEmpty());
        equal(m1.toString(), m2.toString());
        check(Arrays.equals(m1.entrySet().toArray(), m2.entrySet().toArray()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void testNavigableMapRemovers(NavigableMap m)
    {
        final Map emptyMap = new HashMap();

        final Map singletonMap = new HashMap();
        singletonMap.put(1, 2);

        abstract class NavigableMapView {
            abstract NavigableMap view(NavigableMap m);
        }

        NavigableMapView[] views = {
            new NavigableMapView() { NavigableMap view(NavigableMap m) {
                return m; }},
            new NavigableMapView() { NavigableMap view(NavigableMap m) {
                return m.headMap(99, true); }},
            new NavigableMapView() { NavigableMap view(NavigableMap m) {
                return m.tailMap(-99, false); }},
            new NavigableMapView() { NavigableMap view(NavigableMap m) {
                return m.subMap(-99, true, 99, false); }},
        };

        abstract class Remover {
            abstract void remove(NavigableMap m, Object k, Object v);
        }

        Remover[] removers = {
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.remove(k), v); }},

            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.descendingMap().remove(k), v); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.descendingMap().headMap(-86, false).remove(k), v); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.descendingMap().tailMap(86, true).remove(k), v); }},

            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.headMap(86, true).remove(k), v); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.tailMap(-86, true).remove(k), v); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                equal(m.subMap(-86, false, 86, true).remove(k), v); }},

            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.keySet().remove(k)); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.navigableKeySet().remove(k)); }},

            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.navigableKeySet().headSet(86, true).remove(k)); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.navigableKeySet().tailSet(-86, false).remove(k)); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.navigableKeySet().subSet(-86, true, 86, false)
                      .remove(k)); }},

            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.descendingKeySet().headSet(-86, false).remove(k)); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.descendingKeySet().tailSet(86, true).remove(k)); }},
            new Remover() { void remove(NavigableMap m, Object k, Object v) {
                check(m.descendingKeySet().subSet(86, true, -86, false)
                      .remove(k)); }},
        };

        for (NavigableMapView view : views) {
            for (Remover remover : removers) {
                try {
                    m.clear();
                    equalMaps(m, emptyMap);
                    equal(m.put(1, 2), null);
                    equalMaps(m, singletonMap);
                    NavigableMap v = view.view(m);
                    remover.remove(v, 1, 2);
                    equalMaps(m, emptyMap);
                } catch (Throwable t) { unexpected(t); }
            }
        }
    }

    private static void testNavigableMap(NavigableMap<Integer,Integer> m)
    {
        clear(m);
        checkNavigableMapKeys(m, 1, null, null, null, null);

        equal(m.put(1, 2), null);
        equal(m.put(3, 4), null);
        equal(m.put(5, 9), null);

        equal(m.put(1, 2), 2);
        equal(m.put(3, 4), 4);
        equal(m.put(5, 6), 9);

        checkNavigableMapKeys(m, 0, null, null,    1,    1);
        checkNavigableMapKeys(m, 1, null,    1,    1,    3);
        checkNavigableMapKeys(m, 2,    1,    1,    3,    3);
        checkNavigableMapKeys(m, 3,    1,    3,    3,    5);
        checkNavigableMapKeys(m, 5,    3,    5,    5, null);
        checkNavigableMapKeys(m, 6,    5,    5, null, null);

        for (final Iterator<Integer> it :
                 (Iterator<Integer>[])
                 new Iterator<?>[] {
                     m.descendingKeySet().iterator(),
                     m.navigableKeySet().descendingIterator()}) {
            equalNext(it, 5);
            equalNext(it, 3);
            equalNext(it, 1);
            check(! it.hasNext());
            THROWS(NoSuchElementException.class,
                   new Fun(){void f(){it.next();}});
        }

        {
            final Iterator<Map.Entry<Integer,Integer>> it
                = m.descendingMap().entrySet().iterator();
            check(it.hasNext()); equal(it.next().getKey(), 5);
            check(it.hasNext()); equal(it.next().getKey(), 3);
            check(it.hasNext()); equal(it.next().getKey(), 1);
            check(! it.hasNext());
            THROWS(NoSuchElementException.class,
                   new Fun(){void f(){it.next();}});
        }
    }


    private static void testNavigableSet(NavigableSet<Integer> s) {
        clear(s);
        checkNavigableSetKeys(s, 1, null, null, null, null);

        check(s.add(1));
        check(s.add(3));
        check(s.add(5));

        check(! s.add(1));
        check(! s.add(3));
        check(! s.add(5));

        checkNavigableSetKeys(s, 0, null, null,    1,    1);
        checkNavigableSetKeys(s, 1, null,    1,    1,    3);
        checkNavigableSetKeys(s, 2,    1,    1,    3,    3);
        checkNavigableSetKeys(s, 3,    1,    3,    3,    5);
        checkNavigableSetKeys(s, 5,    3,    5,    5, null);
        checkNavigableSetKeys(s, 6,    5,    5, null, null);

        for (final Iterator<Integer> it :
                 (Iterator<Integer>[])
                 new Iterator<?>[] {
                     s.descendingIterator(),
                     s.descendingSet().iterator()}) {
            equalNext(it, 5);
            equalNext(it, 3);
            equalNext(it, 1);
            check(! it.hasNext());
            THROWS(NoSuchElementException.class,
                   new Fun(){void f(){it.next();}});
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() { passed++; }
    static void fail() { failed++; Thread.dumpStack(); }
    static void fail(String msg) { System.out.println(msg); fail(); }
    static void unexpected(Throwable t) { failed++; t.printStackTrace(); }
    static void check(boolean cond) { if (cond) pass(); else fail(); }
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else {System.out.println(x + " not equal to " + y); fail();}}
    static void equal2(Object x, Object y) {equal(x, y); equal(y, x);}
    public static void main(String[] args) throws Throwable {
        try { realMain(args); } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Exception("Some tests failed");
    }
    private static abstract class Fun {abstract void f() throws Throwable;}
    private static void THROWS(Class<? extends Throwable> k, Fun... fs) {
          for (Fun f : fs)
              try { f.f(); fail("Expected " + k.getName() + " not thrown"); }
              catch (Throwable t) {
                  if (k.isAssignableFrom(t.getClass())) pass();
                  else unexpected(t);}}
    static byte[] serializedForm(Object obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) { throw new Error(e); }}
    static Object readObject(byte[] bytes)
        throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        return new ObjectInputStream(is).readObject();}
    @SuppressWarnings("unchecked")
    static <T> T serialClone(T obj) {
        try { return (T) readObject(serializedForm(obj)); }
        catch (Exception e) { throw new Error(e); }}
}