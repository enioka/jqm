package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.clusternode.ClusterNode;

@Parameters(commandNames = "Start-Node", commandDescription = "Start an existing node identified by name, waiting for CTRL-C to end.")
class CommandStartNode extends CommandBase
{
    @Parameter(names = { "-n", "--node-name" }, description = "Name of the node to start.", required = true)
    private String nodeName;

    @Override
    int doWork()
    {
        return (new ClusterNode()).startAndWaitEngine(nodeName);
    }
}
