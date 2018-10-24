package com.enioka.jqm.api;

/**
 * A way to communicate between a runner and its caller.
 */
public interface JobRunnerCallback
{
    /**
     * Should the runner use JMX?
     * 
     * @return
     */
    public boolean isJmxEnabled();

    /**
     * Generates the name the runner should use as a JMX bean if it registers a bean.
     * 
     * @return
     */
    public String getJmxBeanName();

    /**
     * Uses the standard client API to mark the JI as killed. Does nothing more.
     */
    public void killThroughClientApi();

    /**
     * Fetches a more or less accurate current run time of the running JI.
     * 
     * @return
     */
    public Long getRunTimeSeconds();

    /**
     * This CL contains /ext and has the bootstrap CL as parent.
     * 
     * @return
     */
    public ClassLoader getExtensionClassloader();

    /**
     * This is the normal CL of the engine. It should never be visible to payloads.
     * 
     * @return
     */
    public ClassLoader getEngineClassloader();
}