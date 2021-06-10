package com.enioka.jqm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;

import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The interface to implement to create a new database adapter. Adapters contain all the database-specific stuff for running JQM on a
 * specific database brand.<br>
 * <br>
 * All implementations must have a no-argument constructor.
 *
 */
public abstract class DbAdapter
{
    public static Logger jqmlogger = LoggerFactory.getLogger(DbAdapter.class);
    protected String[] IDS = new String[] { "ID" };

    /**
     * Query cache.
     */
    protected Map<String, String> queries = new HashMap<>();

    /**
     * Prefix to use for all tables and views.
     */
    protected String tablePrefix = null;

    /**
     * Tests if the adapter is compatible with a given database. When in doubt, answer no.
     *
     * @param product
     *                    the product code (from Connection.getMetaData().getDatabaseProductName()) in lower case.
     * @return true if compatible, false otherwise.
     */
    public abstract boolean compatibleWith(DatabaseMetaData product) throws SQLException;

    /**
     * Adapt the given query template to enable it to run on the target database. This is only called on startup, and the results are
     * cached.
     *
     * @param sql
     *                the SQL query (full text, no terminator).
     * @return a ready to use query.
     */
    public abstract String adaptSql(String sql);

    /**
     * The name of the columns to retrieve for getGeneratedKeys calls. (some dbs want uppercase, other lowercase).
     *
     * @return the list of id columns.
     */
    public String[] keyRetrievalColumn()
    {
        return IDS;
    }

    /**
     * A list of files to run (from the classpath) before running schema upgrades. Default is empty.
     */
    public List<String> preSchemaCreationScripts()
    {
        return new ArrayList<>();
    }

    /**
     * Hook run before creating a new update Statement.
     *
     * @param cnx
     *                an open and ready to use connection to the database. Please return it without any open statement/result set.
     * @param q
     *                the query which will be run. Can be modified by this method.
     * @return a generated ID or null;
     */
    public void beforeUpdate(Connection cnx, QueryPreparation q)
    {
    }

    /**
     * Called after creating the first connection. The adapter should create its caches and do all initialization it requires. Most
     * importantly, the SQL query cache should be created.
     *
     * @param cnx
     *                an open ready to use connection to the database.
     */
    public void prepare(Properties p, Connection cnx)
    {
        this.tablePrefix = p.getProperty("com.enioka.jqm.jdbc.tablePrefix", "");
        queries.putAll(DbImplBase.queries);
        for (Map.Entry<String, String> entry : DbImplBase.queries.entrySet())
        {
            queries.put(entry.getKey(), this.adaptSql(entry.getValue()));
        }
    }

    /**
     * Returns a ready to run SQL text for the given key. (adaptSql does not need to be run on the returned string)
     *
     * @param key
     *                key of the SQL order
     * @return full text to run on the database. Null if key not found.
     */
    public String getSqlText(String key)
    {
        return queries.get(key);
    }

    /**
     * Databases all have different issues when settings null parameters.
     *
     * @param position
     *                     in the statement (sql parameters).
     * @param s
     *                     statement being built.
     */
    public abstract void setNullParameter(int position, PreparedStatement s) throws SQLException;

    /**
     * Adds pagination elements to a query.
     *
     * @param sql
     *                       the full SQL text
     * @param start
     *                       the first included row (start of page). First row is zero, not one.
     * @param stopBefore
     *                       the first excluded row (end of page)
     * @param prms
     *                       the bind variables values. Can be modified.
     * @return the ready to use SQL query for this database.
     */
    public abstract String paginateQuery(String sql, int start, int stopBefore, List<Object> prms);

    /**
     * The polling method. Creating "queues" on the different databases can be extremely different in the different supported RDBMS, so this
     * method allows to fully change the polling method. <br>
     * <br>
     * Default implementation uses the ji_select_poll SQL template query and does retrieve the whole queue without using headSize, so is a
     * performance waste.
     *
     * @param cnx
     *                     a session without active TX.
     * @param queue
     *                     the queue being polled
     * @param headSize
     *                     upper estimate of how many slots are available - i.e. max JI which can be taken from the queue.
     * @return a list of JI, or an empty list. Never null.
     */
    public List<JobInstance> poll(DbConn cnx, Queue queue, int headSize)
    {
        return JobInstance.select(cnx, "ji_select_poll", queue.getId());
    }

    /**
     * Test the exception to determine whether the database is offline.
     * Errors are specific to each type of database
     *
     * @param e exception to test
     * @return true if the database is closed
     */
    public boolean testDbUnreachable(Exception e)
    {
        Throwable cause = e.getCause();
        return (ExceptionUtils.indexOfType(e, SocketTimeoutException.class) != -1)
            || (ExceptionUtils.indexOfType(e, SocketException.class) != -1)
            || (ExceptionUtils.indexOfType(e, SocketTimeoutException.class) != -1)
            || (cause instanceof SQLException && e.getMessage().equals("Failed to validate a newly established connection."));
    }

    /**
     * Trigger a connection close from the DB.
     * Used in tests to simulate a DB failure.
     *
     * @param cnx
     *                an open ready to use connection to the database.
     */
    public void simulateDisconnection(Connection cnx)
    {
    }

}
