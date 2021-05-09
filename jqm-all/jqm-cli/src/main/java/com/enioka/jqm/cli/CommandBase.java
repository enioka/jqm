package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all CLI commands. Command naming must follow powershell cmdlet conventions (see
 * https://docs.microsoft.com/en-us/powershell/developer/cmdlet/approved-verbs-for-windows-powershell-commands)
 */
abstract class CommandBase
{
    protected static Logger jqmlogger = LoggerFactory.getLogger(CommandBase.class);

    @Parameter(names = { "-s", "--settings" }, description = "Path to JQM properties file. Default is resources.xml.", required = false)
    String settingsFile;

    @Parameter(names = { "-h", "--help", "/?", "-?" }, description = "Display command help.", help = true)
    boolean help = false;

    /**
     * Actually run the command.
     */
    abstract int doWork();
}
