package com.enioka.jqm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

public class DbImplOracle extends DbAdapter
{
    @Override
    public boolean compatibleWith(DatabaseMetaData product) throws SQLException
    {
        return product.getDatabaseProductName().toLowerCase().contains("oracle");
    }

    @Override
    public void prepare(Properties p, Connection cnx)
    {
        super.prepare(p, cnx);

        // In some driver versions, trace is enabled by default!
        System.setProperty("oracle.jdbc.Trace", "false");

        // See poll method for everything which is wrong with Oracle and queues.
        queries.put("ji_select_poll",
                String.format("SELECT /*+ FIRST_ROWS */ a.* FROM (%s) a WHERE ROWNUM < ?", queries.get("ji_select_poll")));
    }

    @Override
    public String adaptSql(String sql)
    {
        return sql.replace("MEMORY TABLE", "TABLE").replace(" INTEGER", " NUMBER(10, 0)").replace(" DOUBLE", " DOUBLE PRECISION")
                .replace("UNIX_MILLIS()", "JQM_PK.currval").replace("IN(UNNEST(?))", "IN(?)")
                .replace("CURRENT_TIMESTAMP - 1 MINUTE", "(CURRENT_TIMESTAMP - 1/1440)")
                .replace("CURRENT_TIMESTAMP - ? SECOND", "(CURRENT_TIMESTAMP - ?/86400)").replace("FROM (VALUES(0))", "FROM DUAL")
                .replace("BOOLEAN", "NUMBER(1)").replace("true", "1").replace("false", "0").replace("__T__", this.tablePrefix);
    }

    @Override
    public void beforeUpdate(Connection cnx, QueryPreparation q)
    {
        // There is no way to do parameterized IN(?) queries with Oracle so we must rewrite these queries as IN(?, ?, ?...)
        // This cannot be done at startup, as the ? count may be different for each call.
        // We could also use Oracle-specific arrays... but we prefer to avoid importing the driver here. Also it's not very user friendly,
        // so keep it simple.
        if (q.sqlText.contains("IN(?)"))
        {
            int index = q.sqlText.indexOf("IN(?)");
            int nbIn = 0;
            while (index >= 0)
            {
                index = q.sqlText.indexOf("IN(?)", index + 1);
                nbIn++;
            }

            int nbList = 0;
            ArrayList<Object> newParams = new ArrayList<>(q.parameters.size() + 10);
            for (Object o : q.parameters)
            {
                if (o instanceof List<?>)
                {
                    nbList++;
                    List<?> vv = (List<?>) o;
                    if (vv.size() == 0)
                    {
                        throw new DatabaseException("Cannot do a query with an empty list parameter");
                    }

                    newParams.addAll(vv);

                    String newPrm[] = new String[vv.size()];
                    for (int j = 0; j < vv.size(); j++)
                    {
                        newPrm[j] = "?";
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < vv.size(); j++)
                    {
                        sb.append("?,");
                    }
                    q.sqlText = q.sqlText.replaceFirst("IN\\(\\?\\)", "IN(" + sb.substring(0, sb.length() - 1) + ")");
                }
                else
                {
                    newParams.add(o);
                }
            }
            q.parameters = newParams;

            if (nbList != nbIn)
            {
                throw new DatabaseException("Mismatch: count of list parameters and of IN clauses is different.");
            }
        }
    }

    @Override
    public void setNullParameter(int position, PreparedStatement s) throws SQLException
    {
        // Absolutely stupid: set to null regardless of type.
        s.setObject(position, null);
    }

    @Override
    public String paginateQuery(String sql, int start, int stopBefore, List<Object> prms)
    {
        sql = String.format("SELECT * FROM ( SELECT /*+ FIRST_ROWS */ a.*, ROWNUM rnum FROM (%s) a WHERE ROWNUM < ?) WHERE RNUM >= ?", sql);
        prms.add(stopBefore);
        prms.add(start + 1);
        return sql;
    }

    // Oracle is very problematic in regards to creating queues with tables, probably since it has AQ (Advanced Queuing, a
    // fully-fledged message broker) to create actual queues.
    // We may want to evaluate that framework one day. For now, we actually cannot use a message broker since we use many filters like
    // the Highlander filter. So it will have to wait for a new H handling mode.
    // There is no - to our knowledge - way of polling a table in a single UPDATE on this db.

    @Override
    public List<JobInstance> poll(DbConn cnx, Queue queue, int headSize)
    {
        return JobInstance.select(cnx, "ji_select_poll", queue.getId(), headSize);
    }

    @Override
    public boolean testDbUnreachable(Exception e)
    {
        if (e instanceof SQLException
            && (e.getMessage().contains("Connection is closed")
            || e.getMessage().contains("Closed Connection")))
        {
            return true;
        }
        if (e.getCause() != null
            && (e.getCause().getMessage().contains("Connection is closed")
            || e.getMessage().contains("Closed Connection")))
        {
            return true;
        }
        return super.testDbUnreachable(e);
    }

    @Override
    public void simulateDisconnection(Connection cnx)
    {
        try (PreparedStatement s = cnx.prepareStatement("SELECT SID,SERIAL# FROM GV$SESSION WHERE USERNAME = 'JQM'"))
        {
            ResultSet rs = s.executeQuery();
            if (!rs.next())
            {
                throw new NoResultException("The query returned zero rows when one was expected.");
            }

            String sql = "ALTER SYSTEM DISCONNECT SESSION '" + rs.getInt(1) + ","+ rs.getInt(2) + "' IMMEDIATE";
            PreparedStatement ns = cnx.prepareStatement(sql);
            ns.execute();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

}
