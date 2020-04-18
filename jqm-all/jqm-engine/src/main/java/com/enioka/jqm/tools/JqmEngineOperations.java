package com.enioka.jqm.tools;

public interface JqmEngineOperations
{
    /**
     * Orders the engine to stop and waits for its actual end.
     */
    public void stop();

    public boolean isUpAndRunning();

    public void resume();

    public void pause();
}
