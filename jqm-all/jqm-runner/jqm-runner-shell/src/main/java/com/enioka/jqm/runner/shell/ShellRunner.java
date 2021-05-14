package com.enioka.jqm.runner.shell;

import com.enioka.jqm.runner.api.JobInstanceTracker;
import com.enioka.jqm.api.JobManager;
import com.enioka.jqm.runner.api.JobRunner;
import com.enioka.jqm.runner.api.JobRunnerCallback;

import org.osgi.service.component.annotations.Component;

import com.enioka.jqm.model.JobDef.PathType;
import com.enioka.jqm.model.JobInstance;

/**
 * This runner provides the ability to launch CLI processes.
 */
@Component(property = { "Plugin-Type=JobRunner", "Runner-Type=shell" })
public class ShellRunner implements JobRunner
{
    public ShellRunner()
    {

    }

    @Override
    public boolean canRun(JobInstance toRun)
    {
        PathType type = toRun.getJD().getPathType();
        return type == PathType.DEFAULTSHELLCOMMAND || type == PathType.POWERSHELLCOMMAND || type == PathType.DIRECTEXECUTABLE;
    }

    @Override
    public JobInstanceTracker getTracker(JobInstance toRun, JobManager engineApi, JobRunnerCallback cb)
    {
        return new ShellJobInstanceTracker(toRun, cb, engineApi);
    }
}
