package java.util.concurrent.atomic;

/**
 * An AtomicBoolean maintains a <tt>boolean</tt> value that is updated
 * atomically. See the package specification for description of the
 * general properties it shares with other atomics.
 **/
public class AtomicBoolean implements java.io.Serializable {

    /**
     * Create a new AtomicBoolean with the given initial value.
     * @param initialValue the intial value
     **/
    public AtomicBoolean(boolean initialValue) {
        // for now
    }

    /**
     * Get the current value.
     * @return the current value.
     **/
    public final boolean get() {
        return false; // for now
    }

    /**
     * Atomically set the value to the given update value if the
     * current value is equal to the expected value.  Any given
     * invocation of this operation may fail (return
     * <code>false</code>) spuriously, but repeated invocation when
     * the current value holds the expected value and no other thread
     * is also attempting to set the value will eventually succeed.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     **/
    public final boolean attemptUpdate(boolean expect, boolean update) {
        return false; // for now
    }

    /**
     * Unconditionally set to the given value.
     * @param newValue the new value.
     **/
    public final void set(boolean newValue) {
        // for now
    }

    /**
     * Set to the given value and return the previous value
     * @param newValue the new value.
     * @return the previous value
     **/
    public final boolean getAndSet(boolean newValue) {
        return false; // for now
    }

}
