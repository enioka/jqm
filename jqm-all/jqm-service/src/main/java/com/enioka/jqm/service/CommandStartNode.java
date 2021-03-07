package com.enioka.jqm.service;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.engine.JqmEngine;
import com.enioka.jqm.engine.JqmRuntimeException;

@Parameters(commandNames = "Start-Node", commandDescription = "Start an existing node identified by name, waiting for CTRL-C to end.")
class CommandStartNode extends CommandBase
{
    @Parameter(names = { "-n", "--node-name" }, description = "Name of the node to start.", required = true)
    private String nodeName;

    /**
     * Hack to allow the service to stop the engine more easily from Main.
     */
    static JqmEngine engine;

    @Override
    int doWork()
    {
        try
        {
            engine = new JqmEngine();
            engine.start(nodeName, new EngineCallback(null));
            engine.join();
            return 0;
        }
        catch (JqmRuntimeException e)
        {
            jqmlogger.error("Error running engine");
            return 111;
        }
        catch (Exception e)
        {
            jqmlogger.error("Could not launch the engine named " + nodeName
                    + ". This may be because no node with this name was declared (with command line option createnode).", e);
            throw new JqmRuntimeException("Could not start the engine", e);
        }
    }
}
