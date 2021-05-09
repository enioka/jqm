package com.enioka.jqm.cli;

import com.beust.jcommander.Parameters;
import com.enioka.admin.MetaService;
import com.enioka.api.admin.RRoleDto;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;

@Parameters(commandNames = "Get-Role", commandDescription = "Fetch all roles defined in central configuration.")
class CommandGetRole extends CommandBase
{
    @Override
    int doWork()
    {
        jqmlogger.info("# Roles defined");
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            for (RRoleDto role : MetaService.getRoles(cnx))
            {
                jqmlogger.info("{} - {}", role.getName(), role.getDescription());
            }
        }
        return 0;
    }
}
