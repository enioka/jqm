package com.enioka.jqm.jdbc.impl.mysql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DbAdapter;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.jdbc.QueryPreparation;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

import org.osgi.service.component.annotations.Component;

@Component(service = DbAdapter.class, property = { "Adapter-Type:String=mysql" })
public class DbImplMySql extends DbAdapter
{
    private String sequenceSql, sequenceSqlRetrieval;

    @Override
    public void prepare(Properties p, Connection cnx)
    {
        super.prepare(p, cnx);

        // We do NOT want to use paginateQuery on each poll query as we want polling to be as painless as possible, so we pre-paginate it.
        queries.put("ji_select_poll", queries.get("ji_select_poll") + " LIMIT ?");

        sequenceSqlRetrieval = adaptSql("SELECT next FROM __T__JQM_SEQUENCE WHERE name = ?");
        sequenceSql = adaptSql("UPDATE __T__JQM_SEQUENCE SET next = next + 1 WHERE name = ?");
    }

    @Override
    public String adaptSql(String sql)
    {
        if (sql.contains("CREATE SEQUENCE"))
        {
            return "";
        }
        return sql.replace("MEMORY TABLE", "TABLE").replace("JQM_PK.nextval", "?").replace(" DOUBLE", " DOUBLE PRECISION")
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
        return (product.getDatabaseProductName().contains("MySQL")
                && ((product.getDatabaseMajorVersion() == 5 && product.getDatabaseMinorVersion() >= 6)
                        || (product.getDatabaseMajorVersion() > 5 && product.getDatabaseMajorVersion() < 8)))
                || (product.getDatabaseProductName().contains("MariaDB")
                        && (product.getDatabaseMajorVersion() == 10 && product.getDatabaseMinorVersion() < 3));
    }

    @Override
    public List<String> preSchemaCreationScripts()
    {
        List<String> res = new ArrayList<>();
        res.add("/sql/mysql.sql");
        return res;
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
                        throw new DatabaseException("Cannot do a query whith an empty list parameter");
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

        // Manually generate a new ID for INSERT orders. (with one exception - history inserts do not need a generated ID)
        if (!q.sqlText.startsWith("INSERT INTO") || q.queryKey.startsWith("history_insert"))
        {
            return;
        }

        try (PreparedStatement s = cnx.prepareStatement(sequenceSql); PreparedStatement s2 = cnx.prepareStatement(sequenceSqlRetrieval))
        {
            s.setString(1, "MAIN");
            s.executeUpdate();

            s2.setString(1, "MAIN");
            ResultSet rs = s2.executeQuery();
            if (!rs.next())
            {
                throw new NoResultException("The query returned zero rows when one was expected.");
            }
            q.preGeneratedKey = rs.getInt(1);
            q.parameters.add(0, q.preGeneratedKey);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(q.sqlText + " - " + sequenceSql + " - while fetching new ID from table sequence", e);
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
}
