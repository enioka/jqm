package com.enioka.jqm.cli;

import com.beust.jcommander.JCommander;
import com.enioka.jqm.cli.bootstrap.CommandLine;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = CommandLine.class)
public class CliParserService implements CommandLine
{
    private static final Logger jqmlogger = (Logger) LoggerFactory.getLogger(CliParserService.class);

    @Override
    public int runOsgiCommand(String[] args)
    {
        return CliParserService.runCommand(args);
    }

    public static int runCommand(String[] args)
    {
        // CommonService.setLogFileName("cli");

        // Create parser
        JCommander jc = JCommander.newBuilder().addCommand(new CommandExportJobDef()).addCommand(new CommandExportQueue())
                .addCommand(new CommandGetEngineVersion()).addCommand(new CommandGetJiStatus()).addCommand(new CommandGetNodeCount())
                .addCommand(new CommandGetRole()).addCommand(new CommandImportClusterConfiguration()).addCommand(new CommandImportJobDef())
                .addCommand(new CommandImportQueue()).addCommand(new CommandInstallNodeTemplate()).addCommand(new CommandNewJi())
                .addCommand(new CommandNewNode()).addCommand(new CommandResetRoot()).addCommand(new CommandResetUser())
                .addCommand(new CommandSetWebConfiguration()).addCommand(new CommandStartNode()).addCommand(new CommandStartSingle())
                .addCommand(new CommandUpdateSchema()).build();
        jc.setColumnSize(160);
        jc.setCaseSensitiveOptions(false);

        // We do not bother with a non-command parsing for global help.
        if (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help") || args[0].equals("-?") || args[0].equals("/?")))
        {
            jc.usage();
            return 0;
        }

        // Parse command
        jc.parse(args);
        CommandBase command = (CommandBase) jc.getCommands().get(jc.getParsedCommand()).getObjects().get(0);

        if (command.help)
        {
            jc.usage();
            return 0;
        }

        // It is possible to actually switch from one environment to another by seting a different settings file.
        // (and therefore a different datasource)
        if (command.settingsFile != null)
        {
            jqmlogger.info("Using alternative settings file {}", command.settingsFile);
            // TODO: use config service instead.
            // Helpers.resourceFile = command.settingsFile;
        }

        // Go.
        return command.doWork();
    }
}
