package com.enioka.jqm.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.engine.JqmSingleRunner;

@Parameters(commandNames = "Start-Single", commandDescription = "Internal JQM use only. Starts an already ATTRIBUTED job instance synchronously.")
class CommandStartSingle extends CommandBase
{
    @Parameter(names = { "-i", "--id" }, description = "ID of the job instance to launch.", required = true)
    private int id;

    @Override
    int doWork()
    {
        JobInstance res = JqmSingleRunner.run(id);
        jqmlogger.info("{}", res.getState());
        return 0;
    }
}
