package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;

@Parameters(commandNames = "New-Ji", commandDescription = "Create a new execution request.")
class CommandNewJi extends CommandBase
{
    @Parameter(names = { "-a", "--application" }, description = "Name of job definition (application name) to use.", required = true)
    private String applicationName;

    @Override
    int doWork()
    {
        jqmlogger.info("Will enqueue application named " + applicationName + " without parameter overloads");
        jqmlogger.info("Request ID is: " + JqmClientFactory.getClient().enqueue(applicationName, "CommandLineUser"));
        return 0;
    }
}
