package com.enioka.admin;

import org.apache.log4j.BasicConfigurator;
import org.hsqldb.Server;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;

public class TestCrud
{
    public static Server s;
    protected static Db db;
    protected DbConn cnx = null;

    @BeforeClass
    public static void testInit() throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();

        if (s == null)
        {
            s = new Server();
            s.setDatabaseName(0, "testdbengine");
            s.setDatabasePath(0, "mem:testdbengine");
            s.setLogWriter(null);
            s.setSilent(true);
            s.start();

            JDBCDataSource ds = new JDBCDataSource();
            ds.setDatabase("jdbc:hsqldb:mem:testdbengine");
            db = new Db(ds, true);
        }
    }

    @Before
    public void beforeEachTest()
    {
        cnx = db.getConn();
        MetaService.deleteAllMeta(cnx, true);
        cnx.commit();
    }

    @After
    public void afterEachTest()
    {
        if (cnx != null)
        {
            cnx.close();
        }
    }

    @Test
    public void testGP()
    {
        GlobalParameter dto = new GlobalParameter();
        dto.setKey("houba");
        dto.setValue("hop");
        dto.upsert(cnx);

        GlobalParameter dto2 = new GlobalParameter();
        dto2.setKey("key2");
        dto2.setValue("value2");
        dto2.upsert(cnx);
        cnx.commit();

        Assert.assertEquals("hop", GlobalParameter.getParameter(cnx, "houba", "none"));
        Assert.assertEquals(2, GlobalParameter.selectAll(cnx).size());
    }

    @Test
    public void testUpdateById()
    {
        GlobalParameter dto = new GlobalParameter();
        dto.setKey("houba");
        dto.setValue("hop");
        dto.upsert(cnx);

        Assert.assertNull(dto.getId());

        dto.getByKey(cnx, "houba");
        Assert.assertNotNull(dto.getId());

        int id = dto.getId();

        dto.setKey("marsu");
        dto.setValue("usram");
        dto.upsert(cnx);

        dto = null;

        GlobalParameter dto2 = new GlobalParameter();
        dto2.getById(cnx, id);

        Assert.assertEquals("marsu", dto2.getKey());
        Assert.assertEquals("usram", dto2.getValue());
    }

}
