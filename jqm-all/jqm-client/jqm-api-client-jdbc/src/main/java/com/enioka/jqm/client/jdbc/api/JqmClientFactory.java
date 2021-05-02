package com.enioka.jqm.client.jdbc.api;

import java.util.Properties;

import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.shared.BaseJqmClientFactory;

/**
 * The entry point to create a {@link JqmClient} that will be able to interact with JQM.<br/>
 * {@link JqmClient}s should never be created outside of this factory.<br>
 * The factory also holds the client cache - clients are cached to avoid creating useless objects and connections. (it is possible to create
 * a non-cached client but this is not the default)
 */
public class JqmClientFactory extends BaseJqmClientFactory<JdbcClient>
{
    /**
     * Return the default client. Note this client is shared in the static context. (said otherwise: the same client is always returned
     * inside a same class loading context). The initialization cost is only paid at first call.
     *
     * @return the default client
     */
    public static JqmClient getClient()
    {
        return getClient(null, null, true);
    }

    /**
     * Return a new client that may be cached or not. Given properties are always use when not cached, and only used at creation time for
     * cached clients.
     *
     * @param name
     *            if null, default client. Otherwise, helpful to retrieve cached clients later.
     * @param p
     *            a set of properties. Implementation specific. Unknown properties are silently ignored.
     * @param cached
     *            if false, the client will not be cached and subsequent calls with the same name will return different objects.
     */
    public static JqmClient getClient(String name, Properties p, boolean cached)
    {
        Properties p2 = null;
        if (p == null)
        {
            p2 = props;
        }
        else
        {
            p2 = new Properties(props);
            p2.putAll(p);
        }

        if (!cached)
        {
            return new JdbcClient(props);
        }

        synchronized (clients)
        {
            if (name == null)
            {
                if (defaultClient == null)
                {
                    jqmlogger.trace("creating default client");
                    defaultClient = new JdbcClient(props);
                }
                return defaultClient;
            }
            else
            {
                clients.putIfAbsent(name, new JdbcClient(props));
                return clients.get(name);
            }
        }
    }
}
