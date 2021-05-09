package com.enioka.jqm.cli.bootstrap;

/**
 * Interface implemented by the CLI parsing service. Used as a pivot between the system class loader or the OSGi host and the OSGi world.
 */
public interface CommandLine
{
    /**
     * Parse command and execute it.
     *
     * @param args
     *            CLI arguments
     * @return return code
     */
    public int runOsgiCommand(String[] args);
}
