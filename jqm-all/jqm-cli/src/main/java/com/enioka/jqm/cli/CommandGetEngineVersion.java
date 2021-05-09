package com.enioka.jqm.cli;

import com.beust.jcommander.Parameters;
import com.enioka.jqm.engine.Helpers;

@Parameters(commandNames = "Get-Version", commandDescription = "Fetch the version of the command line tool.")
class CommandGetEngineVersion extends CommandBase
{
    @Override
    int doWork()
    {
        jqmlogger.info("Engine version: " + Helpers.getMavenVersion());
        return 0;
    }
}
