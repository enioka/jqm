package com.enioka.jqm.jdbc.impl.pg;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import com.enioka.jqm.jdbc.DbAdapter;

import org.osgi.service.component.annotations.Component;

@Component(service = DbAdapter.class, property = { "Adapter-Type:String=postgresql" })
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
}
