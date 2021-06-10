package com.enioka.jqm.jdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DbImplDb2 extends DbAdapter
{
    private String sequenceSqlAppHandle;

    @Override
    public void prepare(Properties p, Connection cnx)
    {
        super.prepare(p, cnx);

        // Simpler polling query, as DB2 has a very weird locking model.
        queries.put("ji_update_poll", this.adaptSql(
                "UPDATE __T__JOB_INSTANCE j1 SET NODE=?, STATUS='ATTRIBUTED', DATE_ATTRIBUTION=CURRENT_TIMESTAMP WHERE j1.STATUS='SUBMITTED' AND j1.ID IN "
                        + "(SELECT j2.ID FROM __T__JOB_INSTANCE j2 WHERE j2.STATUS='SUBMITTED' AND j2.QUEUE=? "
                        + "AND (j2.HIGHLANDER=0 OR (j2.HIGHLANDER=1 AND (SELECT COUNT(1) FROM __T__JOB_INSTANCE j3 WHERE j3.STATUS IN('ATTRIBUTED', 'RUNNING') AND j3.JOBDEF=j2.JOBDEF)=0 )) ORDER BY PRIORITY DESC, INTERNAL_POSITION FETCH FIRST ? ROWS ONLY)"));

        sequenceSqlAppHandle = "SELECT APPLICATION_HANDLE, CLIENT_IPADDR, CLIENT_PORT_NUMBER, SESSION_AUTH_ID,"
            + "CURRENT_SERVER, APPLICATION_NAME, CLIENT_PROTOCOL, CLIENT_PLATFORM, CLIENT_HOSTNAME, CONNECTION_START_TIME, APPLICATION_ID, EXECUTION_ID"
            + " FROM TABLE(MON_GET_CONNECTION(cast(NULL as bigint), -2))";
    }

    @Override
    public String adaptSql(String sql)
    {
        return sql.replace("MEMORY TABLE", "TABLE").replace("UNIX_MILLIS()", "JQM_PK.nextval").replace("IN(UNNEST(?))", "IN(?)")
                .replace("FROM (VALUES(0))", "FROM SYSIBM.SYSDUMMY1").replace("BOOLEAN", "SMALLINT").replace("__T__", this.tablePrefix)
                .replace("true", "1").replace("false", "0");
    }

    @Override
    public boolean compatibleWith(DatabaseMetaData product) throws SQLException
    {
        return product.getDatabaseProductName().toLowerCase().contains("db2");
    }

    @Override
    public void beforeUpdate(Connection cnx, QueryPreparation q)
    {
        // DB2 (without compatibility mode) does not support LIMIT/OFFSET nor binding for FETCH FIRST ? ROWS ONLY. So be it.
        if (q.isKey("ji_update_poll"))
        {
            q.sqlText = q.sqlText.replace("FETCH FIRST ? ROWS ONLY", "FETCH FIRST " + q.parameters.get(2) + " ROWS ONLY");
            q.parameters.remove(2);
        }

        // There is no (clean) way to do parameterized IN(?) queries with DB2 so we must rewrite these queries as IN(?, ?, ?...)
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
        // Issue here is that DB2 does not support the efficient LIMIT/OFFSET or ROWNUM without compatibility options.
        // The commented code below only has one sub query but does not work with UNION queries...
        /*
         * String sb = sql.split("ORDER BY")[1]; sql = sql.split("ORDER BY")[0]; sql = sql.replace(" FROM ",
         * String.format(", ROW_NUMBER() OVER(ORDER BY %s) as rn FROM ", sb)); sql =
         * String.format("SELECT * FROM (%s) WHERE rn BETWEEN ? AND ?", sql);
         */
        // So we must use an OLAP method as a poor man replacement of ROWNUM. With two sub queries to avoid messing with the initial query.
        // Sigh.
        sql = String.format("SELECT * FROM (SELECT a.*, ROW_NUMBER() OVER() AS rn FROM (%s) a) WHERE rn BETWEEN ? AND ?", sql);
        prms.add(start + 1); // +1 : ROW_NUMBER() is 1-based, not 0-based.
        prms.add(stopBefore);

        return sql;
    }

    @Override
    public boolean testDbUnreachable(Exception e)
    {
        if (e.getCause() != null && e.getCause() instanceof SQLNonTransientException
            && e.getCause().getMessage().equals("connection exception: closed"))
        {
            return true;
        }
        return false;
    }

    @Override
    public void simulateDisconnection(Connection cnx)
    {
        try (PreparedStatement s = cnx.prepareStatement(sequenceSqlAppHandle))
        {
            ResultSet rs = s.executeQuery();
            if (!rs.next())
            {
                throw new NoResultException("The query returned zero rows when one was expected.");
            }

            String sql = "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (" + rs.getInt(1) + ")')";
            PreparedStatement ns = cnx.prepareStatement(sql);
            ns.execute();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

}
