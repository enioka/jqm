package com.enioka.jqm.client.shared;

import java.security.InvalidParameterException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.enioka.jqm.client.api.JqmClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 *
 * Factorization of methods common to all client factories.
 *
 * @param <T>
 *            the type of {@link JqmClient} built by this factory.
 */
public abstract class BaseJqmClientFactory<T extends JqmClient>
{
    protected static Logger jqmlogger = LoggerFactory.getLogger(BaseJqmClientFactory.class);

    protected static Properties props = new Properties();
    protected static ConcurrentMap<String, JqmClient> clients = new ConcurrentHashMap<String, JqmClient>();
    protected static JqmClient defaultClient;

    /**
     * Most client providers use a specific configuration file (such as persistence.xml for the Hibernate provider). However, it may be
     * desired to overload these values with runtime values. This method enables a client to specify these values.<br>
     * <strong>Note that the parameter names depend on the provider!</strong><br>
     * Moreover, changing the properties only impact <code>JqmClient</code>s created after the modification. It is important to keep in mind
     * that created <code>JqmClient</code>s are cached - therefore it is customary to use this function <strong>before creating any
     * clients</strong>.
     *
     * @param properties
     *            a non null property bag
     * @throws InvalidParameterException
     *             if props is null
     */
    public static void setProperties(Properties properties)
    {
        if (props == null)
        {
            throw new InvalidParameterException("props cannot be null");
        }
        BaseJqmClientFactory.props = properties;
    }

    /**
     * See {@link #setProperties(Properties)}
     *
     * @param key
     *            a non null non empty parameter key.
     * @param value
     *            value of the parameter.
     */
    public static void setProperty(String key, String value)
    {
        if (props == null)
        {
            throw new IllegalStateException("properties are null");
        }
        if (key == null || key.isEmpty())
        {
            throw new InvalidParameterException("key cannot be empty or null");
        }
        props.setProperty(key, value);
    }

    /**
     * Remove the client of the given name from the static cache. Next time {@link #getClient(String, Properties, boolean)} is called,
     * initialization cost will have to be paid once again.<br>
     * Use <code>null</code> to reset the default client.<br>
     * This method is mostly useful for tests when databases are reset and therefore clients become invalid as they hold connections to
     * them.<br>
     * If the name does not exist, no exception is thrown.
     *
     * @param name
     *            the client to reset, or <code>null</code> for the default client
     */
    public static void resetClient(String name)
    {
        if (name != null)
        {
            synchronized (clients)
            {
                if (clients.containsKey(name))
                {
                    jqmlogger.trace("resetting client " + name);
                    clients.get(name).dispose();
                    clients.remove(name);
                }
            }
        }
        else
        {
            synchronized (clients)
            {
                if (defaultClient != null)
                {
                    jqmlogger.trace("resetting default client");
                    defaultClient.dispose();
                    defaultClient = null;
                }
            }
        }
    }

    /**
     * This is {@link #resetClient(String)} with name = null (i.e. reset the default client).
     */
    public static void resetClient()
    {
        resetClient(null);
    }
}
