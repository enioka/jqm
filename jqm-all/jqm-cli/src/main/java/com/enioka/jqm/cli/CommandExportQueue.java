package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.xml.XmlQueueExporter;

@Parameters(commandNames = "Export-Queue", commandDescription = "Export all queue definitions inside central configuration as a single file.")
class CommandExportQueue extends CommandBase
{
    @Parameter(names = { "-f", "--file" }, description = "Name of the new file.", required = true)
    private String xmlPath;

    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            XmlQueueExporter.export(xmlPath, cnx);
            return 0;
        }
        catch (Exception ex)
        {
            jqmlogger.error("Could not create the export file", ex);
            return 106;
        }
    }
}
