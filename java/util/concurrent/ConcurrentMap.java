package java.util.concurrent;

import java.util.Map;

/**
 * A ConcurrentMap is a Map providing an additional atomic
 * <tt>putIfAbsent</tt> method.
 **/
public interface ConcurrentMap extends Map {
    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * This is equivalent to 
     * <pre>
     *   if (!map.containsKey(key)) map.put(key, value);
     *   return get(key);
     * </pre>
     * Except that the action is performed atomically.
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or <tt>null</tt>
     *	       if there was no mapping for key.  A <tt>null</tt> return can
     *	       also indicate that the map previously associated <tt>null</tt>
     *	       with the specified key, if the implementation supports
     *	       <tt>null</tt> values.
     * 
     * @throws UnsupportedOperationException if the <tt>put</tt> operation is
     *	          not supported by this map.
     * @throws ClassCastException if the class of the specified key or value
     * 	          prevents it from being stored in this map.
     * @throws IllegalArgumentException if some aspect of this key or value
     *	          prevents it from being stored in this map.
     * @throws NullPointerException this map does not permit <tt>null</tt>
     *            keys or values, and the specified key or value is
     *            <tt>null</tt>.
     * 
     **/
    public Object putIfAbsent(Object key, Object value);
}
