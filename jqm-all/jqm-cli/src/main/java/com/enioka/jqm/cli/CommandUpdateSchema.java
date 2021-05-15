package com.enioka.jqm.cli;

import java.util.Properties;

import com.beust.jcommander.Parameters;
import com.enioka.admin.MetaService;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.DbManager;

@Parameters(commandNames = "Update-Schema", commandDescription = "Updates the database schema.")
class CommandUpdateSchema extends CommandBase
{
    @Override
    int doWork()
    {
        jqmlogger.info("Database connector initialization");
        Properties p = new Properties();
        p.setProperty("com.enioka.jqm.jdbc.allowSchemaUpdate", "true");
        Db db = DbManager.getDb(p);

        try (DbConn cnx = db.getConn())
        {
            Helpers.updateConfiguration(cnx);
            cnx.commit();
            jqmlogger.info("Upgrade done");
            jqmlogger.info("Existing nodes: " + MetaService.getNodes(cnx).size());
            return 0;
        }
        catch (Exception e)
        {
            jqmlogger.error("Could not upgrade", e);
            return 101;
        }
    }
}
