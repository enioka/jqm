package com.enioka.jqm.ws.plumbing.shiro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.util.CollectionUtils;
import org.apache.shiro.util.SoftHashMap;

/**
 * Heavy copy/paste from Shiro's MapCache.
 *
 * This cache is built upon Shiro's {@link SoftHashMap SoftHashMap} which in
 * turn uses soft references to avoid any memory leak. It keeps track of the
 * moment the entry was created and declares entries obsolete after a given
 * amount of time.
 *
 * No housekeeping is done - the time limit is simply checked when the value is
 * asked for. Actual purge of items falls upon the garbage collector (thanks to
 * the soft links).
 */
public class UserCache<K, V> implements Cache<K, V> {
    /**
     * The name of this cache.
     */
    private final String name;

    /**
     * Real cache
     */
    private Map<K, TimeCacheEntry<V>> cache = new SoftHashMap<K, UserCache<K, V>.TimeCacheEntry<V>>();

    private class TimeCacheEntry<Z> {
        Z entry;
        Calendar lastReset = Calendar.getInstance();

        TimeCacheEntry(Z entry) {
            this.entry = entry;
        }

        boolean isValid() {
            Calendar limit = Calendar.getInstance();
            limit.add(Calendar.MINUTE, -10);
            return this.lastReset.after(limit);
        }
    }

    public UserCache(String name) {
        this.name = name;
    }

    public V get(K key) throws CacheException {
        if (cache.containsKey(key) && cache.get(key).isValid()) {
            return cache.get(key).entry;
        }
        return null;
    }

    public V put(K key, V value) throws CacheException {
        V existing = cache.get(key) != null ? cache.get(key).entry : null;
        cache.put(key, new TimeCacheEntry<V>(value));
        return existing;
    }

    public V remove(K key) throws CacheException {
        V existing = cache.get(key) != null ? cache.get(key).entry : null;
        cache.remove(key);
        return existing;
    }

    public void clear() throws CacheException {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Set<K> keys() {
        Set<K> keys = new HashSet<>();
        for (Map.Entry<K, TimeCacheEntry<V>> e : cache.entrySet()) {
            if (e.getValue().isValid()) {
                keys.add(e.getKey());
            }
        }
        if (!keys.isEmpty()) {
            return Collections.unmodifiableSet(keys);
        }
        return Collections.emptySet();
    }

    public Collection<V> values() {
        Collection<V> values = new ArrayList<>();
        for (TimeCacheEntry<V> e : this.cache.values()) {
            if (e.isValid()) {
                values.add(e.entry);
            }
        }
        if (!CollectionUtils.isEmpty(values)) {
            return Collections.unmodifiableCollection(values);
        }
        return Collections.emptySet();
    }

    public String toString() {
        return new StringBuilder("MapCache '").append(name).append("' (").append(cache.size()).append(" entries)")
                .toString();
    }

}
