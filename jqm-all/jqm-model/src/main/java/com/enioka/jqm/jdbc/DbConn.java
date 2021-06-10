package com.enioka.jqm.jdbc;

import java.io.Closeable;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: better way to close statements and RS.

/**
 * Db querying utility.
 */
public class DbConn implements Closeable
{
    private static Logger jqmlogger = LoggerFactory.getLogger(DbConn.class);
    private static Calendar utcZone = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private Db parent;
    Connection _cnx;
    private boolean transac_open = false;
    private boolean rollbackOnly = false;
    private List<Statement> toClose = new ArrayList<>();

    DbConn(Db parent, Connection cnx)
    {
        this.parent = parent;
        this._cnx = cnx;
    }

    public void commit()
    {
        if (rollbackOnly)
        {
            throw new IllegalStateException("cannot commit a rollback only session. Use rollback first.");
        }
        try
        {
            _cnx.commit();
            transac_open = false;
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }
    }

    public void rollback()
    {
        try
        {
            _cnx.rollback();
            transac_open = false;
            rollbackOnly = false;
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }
    }

    public void setRollbackOnly()
    {
        rollbackOnly = true;
    }

    private QueryPreparation adapterPreparation(String query_key, boolean forUpdate, Object... params)
    {
        QueryPreparation qp = new QueryPreparation();
        qp.parameters = new ArrayList<>(Arrays.asList(params));
        qp.queryKey = query_key;
        qp.sqlText = parent.getQuery(query_key);
        qp.forUpdate = forUpdate;

        this.parent.getAdapter().beforeUpdate(_cnx, qp);
        return qp;
    }

    /** For testing purposes only */
    public void runCommand(String query_key, Object... params)
    {
        QueryPreparation qp = adapterPreparation(query_key, false, params);
        try (PreparedStatement ps = prepare(qp))
        {
            ps.execute();
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            jqmlogger.warn(e.getMessage());
        }
    }

    public QueryResult runUpdate(String query_key, Object... params)
    {
        transac_open = true;
        QueryPreparation qp = adapterPreparation(query_key, false, params);
        try (PreparedStatement ps = prepare(qp))
        {
            QueryResult qr = new QueryResult();
            qr.nbUpdated = ps.executeUpdate();
            qr.generatedKey = qp.preGeneratedKey;
            if (qr.generatedKey == null && query_key.contains("insert") && !query_key.equals("history_insert_with_end_date"))
            {
                ResultSet gen = ps.getGeneratedKeys();
                if (gen.next())
                {
                    try
                    {
                        qr.generatedKey = gen.getInt(1);
                    }
                    catch (SQLException e)
                    {
                        // nothing to do.
                    }
                }
            }

            jqmlogger.debug("Updated rows: {}. Key: {}. Generated ID: {}", qr.nbUpdated, query_key, qr.generatedKey);
            return qr;
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(qp.sqlText, e);
        }
    }

    void runRawUpdate(String query_sql)
    {
        transac_open = true;
        String sql = null;
        try (Statement s = _cnx.createStatement())
        {
            sql = parent.getAdapter().adaptSql(query_sql);
            if (sql.trim().isEmpty())
            {
                return;
            }
            jqmlogger.debug(sql);
            s.executeUpdate(sql);
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(sql, e);
        }
    }

    public ResultSet runRawSelect(String rawQuery, Object... params)
    {
        PreparedStatement ps = null;
        QueryPreparation q = new QueryPreparation();
        q.parameters = new ArrayList<>(Arrays.asList(params));
        q.sqlText = this.parent.getAdapter().adaptSql(rawQuery);
        this.parent.getAdapter().beforeUpdate(_cnx, q);

        try
        {
            jqmlogger.debug("Running raw SQL query: {}", q.sqlText);
            for (Object o : params)
            {
                if (o == null)
                {
                    jqmlogger.debug("     null");
                }
                else
                {
                    jqmlogger.debug("     {} - {}", o.toString(), o.getClass());
                }
            }

            ps = _cnx.prepareStatement(q.sqlText, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            toClose.add(ps);
            int i = 0;
            for (Object prm : q.parameters)
            {
                addParameter(prm, ++i, ps);
            }

            return ps.executeQuery();
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(q.sqlText, e);
        }
        finally
        {
            // closeQuietly(ps); // Closed when cnx is closed.
        }
    }

    public ResultSet runSelect(String query_key, Object... params)
    {
        return runSelect(false, query_key, params);
    }

    public ResultSet runSelect(boolean for_update, String query_key, Object... params)
    {
        PreparedStatement ps = null;
        QueryPreparation qp = adapterPreparation(query_key, for_update, params);
        try
        {
            ps = prepare(qp);
            toClose.add(ps);
            if (for_update)
            {
                transac_open = true;
            }
            return ps.executeQuery();
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(qp.sqlText, e);
        }
        finally
        {
            // closeQuietly(ps);
        }
    }

    public Map<String, Object> runSelectSingleRow(String query_key, Object... params)
    {
        HashMap<String, Object> res = new HashMap<>();
        try (ResultSet rs = runSelect(query_key, params))
        {
            if (!rs.next())
            {
                throw new NoResultException("The query returned zero rows when one was expected.");
            }
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++)
            {
                // We take the type as returned, with an exception for small numerics (we do not want long or BigInt which cannot be cast)
                Object or;
                if (meta.getColumnType(i) == java.sql.Types.NUMERIC && meta.getPrecision(i) <= 10)
                {
                    or = rs.getInt(i);
                }
                else
                {
                    or = rs.getObject(i);
                }
                res.put(meta.getColumnName(i).toUpperCase(), or);
            }

            if (rs.next())
            {
                throw new NonUniqueResultException("The query returned more than one row when one was expected");
            }
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }
        return res;
    }

    public <T> T runSelectSingle(String query_key, Class<T> clazz, Object... params)
    {
        return runSelectSingle(query_key, 1, clazz, params);
    }

    @SuppressWarnings("unchecked")
    public <T> T runSelectSingle(String query_key, int column, Class<T> clazz, Object... params)
    {
        try (ResultSet rs = runSelect(query_key, params))
        {
            if (rs.getMetaData().getColumnCount() < 1)
            {
                throw new DatabaseException("query was supposed to return at least " + (column) + " columns - "
                        + rs.getMetaData().getColumnCount() + " were returned.");
            }

            T res;
            if (rs.next())
            {
                if (clazz.equals(Integer.class))
                {
                    res = (T) (Integer) rs.getInt(column);
                }
                else if (clazz.equals(String.class))
                {
                    res = (T) rs.getString(column);
                }
                else if (clazz.equals(Calendar.class))
                {
                    res = (T) getCal(rs, column);
                }
                else if (clazz.equals(Long.class))
                {
                    res = (T) (Long) rs.getLong(column);
                }
                else if (clazz.equals(Float.class))
                {
                    res = (T) (Float) rs.getFloat(column);
                }
                else
                {
                    throw new DatabaseException("unsupported single query return type " + clazz.getCanonicalName());
                }
            }
            else
            {
                throw new NoResultException("query was supposed to return a single row - none returned");
            }
            if (rs.next())
            {
                throw new NonUniqueResultException("query was supposed to return a single row - multiple returned");
            }

            return res;
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }
    }

    public <T> List<T> runSelectColumn(String query_key, Class<T> clazz, Object... params)
    {
        return runSelectColumn(query_key, 1, clazz, params);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> runSelectColumn(String query_key, int column, Class<T> clazz, Object... params)
    {
        ArrayList<T> resList = new ArrayList<>();
        try (ResultSet rs = runSelect(query_key, params))
        {
            if (rs.getMetaData().getColumnCount() < column)
            {
                throw new DatabaseException("query was supposed to return at least " + (column) + " columns - "
                        + rs.getMetaData().getColumnCount() + " were returned.");
            }

            T res;
            while (rs.next())
            {
                if (clazz.equals(Integer.class))
                {
                    res = (T) (Integer) rs.getInt(column);
                }
                else if (clazz.equals(String.class))
                {
                    res = (T) rs.getString(column);
                }
                else if (clazz.equals(Calendar.class))
                {
                    res = (T) getCal(rs, column);
                }
                else if (clazz.equals(Long.class))
                {
                    res = (T) (Long) rs.getLong(column);
                }
                else if (clazz.equals(Float.class))
                {
                    res = (T) (Float) rs.getFloat(column);
                }
                else
                {
                    throw new DatabaseException("unsupported single query return type " + clazz.getCanonicalName());
                }
                resList.add(res);
            }

            return resList;
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }
    }

    /**
     * Close all JDBC objects related to this connection.
     */
    public void close()
    {
        if (transac_open)
        {
            try
            {
                this._cnx.rollback();
            }
            catch (Exception e)
            {
                // Ignore.
            }
        }

        for (Statement s : toClose)
        {
            closeQuietly(s);
        }
        toClose.clear();

        closeQuietly(_cnx);
        _cnx = null;
    }

    /**
     * Close utility method.
     *
     * @param ps
     *               statement to close.
     */
    public void closeQuietly(Closeable ps)
    {
        DbHelper.closeQuietly(ps);
    }

    /**
     * Close utility method.
     *
     * @param ps
     *               statement to close through a result set.
     */
    public void closeQuietly(ResultSet ps)
    {
        DbHelper.closeQuietly(ps);
    }

    /**
     * Close utility method.
     *
     * @param ps
     *               statement to close.
     */
    public void closeQuietly(Connection ps)
    {
        DbHelper.closeQuietly(ps);
    }

    /**
     * Close utility method.
     *
     * @param ps
     *               statement to close.
     */
    public void closeQuietly(Statement ps)
    {
        DbHelper.closeQuietly(ps);
    }

    private PreparedStatement prepare(QueryPreparation q)
    {
        PreparedStatement ps = null;
        if (_cnx == null)
        {
            throw new IllegalStateException("Connection does not exist");
        }

        if (q.sqlText == null || q.sqlText.trim().isEmpty())
        {
            throw new DatabaseException("unknown query key");
        }

        // Debug
        jqmlogger.debug("Running {} : {} with {} parameters.", q.queryKey, q.sqlText, q.parameters.size());
        for (Object o : q.parameters)
        {
            if (o == null)
            {
                jqmlogger.debug("      null");
            }
            else
            {
                jqmlogger.debug("      {} - {}", o.toString(), o.getClass());
            }
        }

        try
        {
            if (q.forUpdate)
            {
                ps = _cnx.prepareStatement(q.sqlText, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
            }
            else
            {
                ps = _cnx.prepareStatement(q.sqlText, this.parent.getAdapter().keyRetrievalColumn());
            }
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException(e);
        }

        // Add parameters
        int i = 0;
        for (Object prm : q.parameters)
        {
            addParameter(prm, ++i, ps);
        }

        return ps;
    }

    private void addParameter(Object value, int position, PreparedStatement s)
    {
        // TODO: cache meta call and use setObject.
        try
        {
            if (value == null)
            {
                parent.getAdapter().setNullParameter(position, s);
            }
            else if (Integer.class == value.getClass())
                s.setInt(position, (Integer) value);
            else if (Long.class == value.getClass())
                s.setLong(position, (Long) value);
            else if (String.class == value.getClass())
                s.setString(position, (String) value);
            else if (Timestamp.class == value.getClass())
                s.setTimestamp(position, (Timestamp) value);
            else if (Time.class == value.getClass())
                s.setTime(position, (Time) value);
            else if (Boolean.class == value.getClass())
                s.setBoolean(position, (Boolean) value);
            else if (value instanceof Calendar)
                s.setTimestamp(position, new Timestamp(((Calendar) value).getTimeInMillis()), utcZone);
            else if (value instanceof List<?>)
            {
                Array a;
                List<?> vv = (List<?>) value;
                if (vv.size() == 0)
                {
                    throw new DatabaseException("Cannot do a query with an empty list parameter");
                }
                if (vv.get(0) instanceof Integer)
                {
                    a = _cnx.createArrayOf("INTEGER", ((List<?>) value).toArray(new Integer[0]));
                }
                else if (vv.get(0) instanceof String)
                {
                    a = _cnx.createArrayOf("VARCHAR", ((List<?>) value).toArray(new String[0]));
                }
                else
                {
                    String[] vvv = new String[vv.size()];
                    int i = 0;
                    for (Object o : vv)
                    {
                        vvv[i++] = o.toString();
                    }
                    a = _cnx.createArrayOf("VARCHAR", vvv);
                }
                s.setArray(position, a);
            }
            else
            {
                s.setString(position, value.toString());
            }
        }
        catch (SQLException e)
        {
            if (this.parent.getAdapter().testDbUnreachable(e))
            {
                throw new DatabaseUnreachableException(e);
            }
            throw new DatabaseException("Could not set parameter at position " + position, e);
        }
    }

    public Calendar getCal(ResultSet rs, int colIdx) throws SQLException
    {
        Calendar c = null;
        if (rs.getTimestamp(colIdx) != null)
        {
            c = Calendar.getInstance();
            c.setTimeInMillis(rs.getTimestamp(colIdx, utcZone).getTime());
        }
        return c;
    }

    public String paginateQuery(String sql, int start, int stopBefore, List<Object> prms)
    {
        return this.parent.getAdapter().paginateQuery(sql, start, stopBefore, prms);
    }

    public void logDatabaseInfo(Logger log)
    {
        try
        {
            DatabaseMetaData m = this._cnx.getMetaData();
            log.info("Database driver {} version {} on database {} version {}.{} (product {}). Adapter is {}.", m.getDriverName(),
                    m.getDriverVersion(), m.getDatabaseProductName(), m.getDatabaseMajorVersion(), m.getDatabaseMinorVersion(),
                    m.getDatabaseProductVersion(), parent.getAdapter().getClass().getName());
        }
        catch (SQLException e1)
        {
            if (this.parent.getAdapter().testDbUnreachable(e1))
            {
                throw new DatabaseUnreachableException(e1);
            }
            jqmlogger.warn("Could not fetch database version", e1);
        }
    }

    public List<JobInstance> poll(Queue queue, int nbSlots)
    {
        return this.parent.getAdapter().poll(this, queue, nbSlots);
    }

    public void simulateDisconnection()
    {
        this.parent.getAdapter().simulateDisconnection(_cnx);
    }
}
