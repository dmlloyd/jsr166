package java.util.concurrent.atomic;
/**
 * An AtomicStampedReference maintains an object reference along with an
 * integer "stamp", that can be updated atomically.
 **/

public class AtomicStampedReference<V> {
    private class ReferenceIntegerPair {
        private final V reference;
        private final int integer;
        ReferenceIntegerPair(V r, int i) {
            reference = r; integer = i;
        }
    }

    private final AtomicReference<ReferenceIntegerPair> atomicRef;

    /**
     * Create a new AtomicStampableReference with the given initial values
     * @param initialRef the intial reference
     * @param initialStamp the intial stamp
     **/
    public AtomicStampedReference(V initialRef, int initialStamp) {
        atomicRef = new AtomicReference<ReferenceIntegerPair>(new ReferenceIntegerPair(initialRef, initialStamp));
    }

    /**
     * Get the current value of the reference.
     * @return the current value of the reference.
     **/
    public V getReference() {
        return ((ReferenceIntegerPair)(atomicRef.get())).reference;
    }

    /**
     * Get the current value of the stamp
     * @return the current value of the stamp.
     **/
    public int getStamp() {
        return ((ReferenceIntegerPair)(atomicRef.get())).integer;
    }

    /**
     * Get the current values of both the reference and the stamp.
     * Typical usage is <code>int[1] holder; ref = v.get(holder); </code>.
     * @param stampHolder an array of size of at least one; on
     * return stampholder[0] will hold the value of the stamp.
     * @return the current value of the reference.
     **/
    public V get(int[] stampHolder) {
        ReferenceIntegerPair p = (ReferenceIntegerPair)(atomicRef.get());
        stampHolder[0] = p.integer;
        return p.reference;
    }


    /**
     * Atomically set the value of both the reference and stamp
     * to the given update values if the
     * current reference is <code>==</code> to the expected reference
     * and the current stamp is equal to the expected stamp.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current calue holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     * @param expectedReference the expected value of the reference
     * @param newReference the new value for the reference
     * @param expectedStamp the expected value of the stamp
     * @param newStamp the new value for the stamp
     * @return true if successful
     */
    public boolean attemptUpdate(V      expectedReference,
                                 V      newReference,
                                 int    expectedStamp,
                                 int    newStamp) {
        ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
        return  expectedReference == current.reference &&
            expectedStamp == current.integer &&
            ((newReference == current.reference && newStamp == current.integer) ||
             atomicRef.attemptUpdate(current,
                                     new ReferenceIntegerPair(newReference,
                                                              newStamp)));
    }

    /**
     * Unconditionally set the value of both the reference and stamp.
     * @param newReference the new value for the reference
     * @param newStamp the new value for the stamp
     */
    public void set(V newReference, int newStamp) {
        ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
        if (newReference != current.reference || newStamp != current.integer)
            atomicRef.set(new ReferenceIntegerPair(newReference, newStamp));
    }

    /**
     * Atomically set the value of the stamp to the given update value
     * if the current reference is <code>==</code> to the expected
     * reference.  Any given invocation of this operation may fail
     * (return <code>false</code>) spuriously, but repeated invocation
     * when the current calue holds the expected value and no other
     * thread is also attempting to set the value will eventually
     * succeed.
     * @param expectedReference the expected value of the reference
     * @param newStamp the new value for the stamp
     * @return true if successful
     */
    public boolean attemptStamp(V expectedReference, int newStamp) {
        ReferenceIntegerPair current = (ReferenceIntegerPair)(atomicRef.get());
        return  expectedReference == current.reference &&
            (newStamp == current.integer ||
             atomicRef.attemptUpdate(current,
                                     new ReferenceIntegerPair(expectedReference,
                                                              newStamp)));
    }


}

