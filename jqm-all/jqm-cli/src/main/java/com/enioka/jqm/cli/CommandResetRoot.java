package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.admin.MetaService;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;

@Parameters(commandNames = "Reset-Root", commandDescription = "Create or reset the 'root' account password and permissions.")
class CommandResetRoot extends CommandBase
{
    @Parameter(names = { "-p", "--password" }, description = "New password.", required = true)
    private String password;

    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            Helpers.createRoleIfMissing(cnx, "administrator", "all permissions without exception", "*:*");
            Helpers.createUserIfMissing(cnx, "root", password, "all powerful user", "administrator");
            MetaService.changeUserPassword(cnx, "root", password);
            cnx.commit();
            return 0;
        }
        catch (Exception ex)
        {
            jqmlogger.error("Could not reset the root account", ex);
            return 108;
        }
    }
}
