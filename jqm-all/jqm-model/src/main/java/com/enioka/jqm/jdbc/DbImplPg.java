package com.enioka.jqm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;

public class DbImplPg extends DbAdapter
{
    public DbImplPg()
    {
        this.IDS[0] = "id";
    }

    @Override
    public String adaptSql(String sql)
    {
        return sql.replace("MEMORY TABLE", "TABLE").replace("JQM_PK.nextval", "nextval('JQM_PK')").replace(" DOUBLE", " DOUBLE PRECISION")
                .replace(" REAL", " DOUBLE PRECISION").replace("UNIX_MILLIS()", "extract('epoch' from current_timestamp)*1000")
                .replace("IN(UNNEST(?))", "=ANY(?)").replace("CURRENT_TIMESTAMP - 1 MINUTE", "NOW() - INTERVAL '1 MINUTES'")
                .replace("CURRENT_TIMESTAMP - ? SECOND", "(NOW() - (? || ' SECONDS')::interval)").replace("FROM (VALUES(0))", "")
                .replace("__T__", this.tablePrefix);
    }

    @Override
    public boolean compatibleWith(DatabaseMetaData product) throws SQLException
    {
        return product.getDatabaseProductName().toLowerCase().contains("postgresql");
    }

    @Override
    public void setNullParameter(int position, PreparedStatement s) throws SQLException
    {
        s.setNull(position, s.getParameterMetaData().getParameterType(position));
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
    public boolean testDbUnreachable(Exception e)
    {
        if (ExceptionUtils.indexOfType(e, SQLNonTransientConnectionException.class) != -1)
        {
            return true;
        }
        if (e instanceof SQLException
            && (e.getMessage().equals("Failed to validate a newly established connection.")
            || e.getMessage().contains("FATAL: terminating connection due to administrator command")
            || e.getMessage().contains("This connection has been closed")
            || e.getMessage().contains("Communications link failure")
            || e.getMessage().contains("Connection is closed")))
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
            PreparedStatement s = cnx.prepareStatement("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='jqm'");
            s.execute();
            // pg_terminate_backend sends a SIGTERM signal to backend process, leave some time to terminate the preocess
            Thread.sleep(5000);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        catch (InterruptedException e)
        {
            // not an issue in tests
        }
    }

}
