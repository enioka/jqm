package com.enioka.jqm.ws.plumbing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This allows to control the Cache-Control header of HTTP answers to clients. The different elements of this header can be (separated by
 * commas):
 * <ul>
 * <li>private: caching allowed only on the client itself (not proxies, etc)</li>
 * <li>public: everyone is allowed to cache (client, proxies, optimizers, ...)</li>
 * <li>no-cache: should never be cached by anyone</li>
 * <li>no-store: can be cached by everyone but never written to disk</li>
 * <li>no-transform: can be cached by everyone but never altered (such as: no picture compression)</li>
 * <li>max-age: cache validity (in seconds)</li>
 * <li>s-maxage: same as max-age but only for non clients</li>
 * </ul>
 * For example, <code>@HttpCache("private, max-age=3600")</code> will allow clients (and only clients) to cache the data for one hour.<br>
 * Default is <code>no-cache</code>.
 */
// @Target(ElementType.METHOD)
// @Retention(RetentionPolicy.RUNTIME)
public @interface HttpCache
{
    String value() default "no-cache";
}
