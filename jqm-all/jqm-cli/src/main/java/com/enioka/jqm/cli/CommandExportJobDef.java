package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.xml.XmlJobDefExporter;

@Parameters(commandNames = "Export-JobDef", commandDescription = "Export all job definitions inside central configuration as a single deployment descriptor.")
class CommandExportJobDef extends CommandBase
{
    @Parameter(names = { "-f", "--file" }, description = "Name of the new deployment descriptor file.", required = true)
    private String xmlPath;

    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            XmlJobDefExporter.export(xmlPath, cnx);
            return 0;
        }
        catch (Exception ex)
        {
            jqmlogger.error("Could not create the file", ex);
            return 106;
        }
    }
}
