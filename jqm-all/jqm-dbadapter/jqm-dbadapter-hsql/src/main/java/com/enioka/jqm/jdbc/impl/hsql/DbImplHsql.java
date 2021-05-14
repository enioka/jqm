package com.enioka.jqm.jdbc.impl.hsql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import com.enioka.jqm.jdbc.DbAdapter;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

import org.osgi.service.component.annotations.Component;

@Component(service = DbAdapter.class, property = { "Adapter-Type:String=hsql" })
public class DbImplHsql extends DbAdapter
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
        return sql.replace("JQM_PK.nextval", "NEXT VALUE FOR JQM_PK").replace("FOR UPDATE LIMIT", "LIMIT").replace("__T__",
                this.tablePrefix);
    }

    @Override
    public boolean compatibleWith(DatabaseMetaData product) throws SQLException
    {
        return product.getDatabaseProductName().toLowerCase().contains("hsql");
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
