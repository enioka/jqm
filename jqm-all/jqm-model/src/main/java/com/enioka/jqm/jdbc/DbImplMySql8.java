package com.enioka.jqm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;

import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

/**
 * Should work on MySQL 8+ and MariaDB 10.3+. These versions have introduced persistent AUTO_INCREMENT high water mark.
 */
public class DbImplMySql8 extends DbAdapter
{
    @Override
    public void prepare(Properties p, Connection cnx)
    {
        super.prepare(p, cnx);

        // We do NOT want to use paginateQuery on each poll query as we want polling to be as painless as possible, so we pre-paginate it.
        queries.put("ji_select_poll", queries.get("ji_select_poll") + " LIMIT ?");
    }

    @Override
    public String adaptSql(String sql)
    {
        if (sql.contains("CREATE SEQUENCE"))
        {
            return "";
        }
        return sql.replace("MEMORY TABLE", "TABLE").replace("ID INTEGER NOT NULL", "ID INTEGER NOT NULL AUTO_INCREMENT")
            .replace("JQM_PK.nextval", "NULL").replace(" DOUBLE", " DOUBLE PRECISION")
            .replace("UNIX_MILLIS()", "ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000)").replace("IN(UNNEST(?))", "IN(?)")
            .replace("CURRENT_TIMESTAMP - 1 MINUTE", "(UNIX_TIMESTAMP() - 60)")
            .replace("CURRENT_TIMESTAMP - ? SECOND", "(NOW() - INTERVAL ? SECOND)").replace("FROM (VALUES(0))", "FROM DUAL")
            .replace("DNS||':'||PORT", "CONCAT(DNS, ':', PORT)").replace(" TIMESTAMP ", " TIMESTAMP(3) ")
            .replace("CURRENT_TIMESTAMP", "FFFFFFFFFFFFFFFFF@@@@").replace("FFFFFFFFFFFFFFFFF@@@@", "CURRENT_TIMESTAMP(3)")
            .replace("TIMESTAMP(3) NOT NULL", "TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)").replace("__T__", this.tablePrefix);
    }

    @Override
    public boolean compatibleWith(DatabaseMetaData product) throws SQLException
    {
        return (product.getDatabaseProductName().contains("MySQL") && (product.getDatabaseMajorVersion() >= 8))
            || (product.getDatabaseProductName().contains("MariaDB")
            && ((product.getDatabaseMajorVersion() == 10 && product.getDatabaseMinorVersion() >= 3)
            || (product.getDatabaseMajorVersion() > 10)));
    }

    @Override
    public void beforeUpdate(Connection cnx, QueryPreparation q)
    {
        // There is no way to do parameterized IN(?) queries with MySQL so we must rewrite these queries as IN(?, ?, ?...)
        // This cannot be done at startup, as the ? count may be different for each call.
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
                } else
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
        int pageSize = stopBefore - start;
        sql = String.format("%s LIMIT ? OFFSET ?", sql);
        prms.add(pageSize);
        prms.add(start);
        return sql;
    }

    @Override
    public List<JobInstance> poll(DbConn cnx, Queue queue, int headSize)
    {
        return JobInstance.select(cnx, "ji_select_poll", queue.getId(), headSize);
    }

    @Override
    public boolean testDbUnreachable(Exception e)
    {
        if (ExceptionUtils.indexOfType(e, SQLNonTransientConnectionException.class) != -1)
        {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause != null
            && (cause.getMessage().contains("This connection has been closed")
            || cause.getMessage().contains("Communications link failure")
            || cause.getMessage().contains("Connection is closed")))
        {
            return true;
        }

        return super.testDbUnreachable(e);
    }

    @Override
    public void simulateDisconnection(Connection cnx)
    {
        try
        {
            PreparedStatement s = cnx.prepareStatement("KILL USER jqm");
            s.execute();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }
}
