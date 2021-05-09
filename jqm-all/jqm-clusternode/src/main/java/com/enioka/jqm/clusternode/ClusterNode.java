package com.enioka.jqm.clusternode;

import com.enioka.jqm.engine.JqmEngine;
import com.enioka.jqm.engine.JqmRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterNode
{
    protected static Logger jqmlogger = LoggerFactory.getLogger(ClusterNode.class);

    private String nodeName;
    private JqmEngine jqmEngine;

    public int startAndWaitEngine(String nodeName)
    {
        this.nodeName = nodeName;

        try
        {
            this.jqmEngine = new JqmEngine();
            jqmEngine.start(nodeName, new EngineCallback(null));
            jqmEngine.join();
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
