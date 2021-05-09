package com.enioka.jqm.clusternode;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;

final class CommonService
{
    private static final Logger jqmlogger = (Logger) LoggerFactory.getLogger(CommonService.class);

    static void setLogFileName(String name)
    {
        try
        {
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            Appender a = root.getAppender("rollingfile");
            if (a == null)
            {
                return;
            }
            RollingFileAppender r = (RollingFileAppender) a;
            r.setFile("./logs/jqm-" + name + ".log");
            r.start();
        }
        catch (Error e)
        {
            jqmlogger.warn("Log file could not be set", e);
        }
    }

    public static void setLogLevel(String level)
    {
        try
        {
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.toLevel(level));

            Logger log = (Logger) LoggerFactory.getLogger("com.enioka");
            log.setLevel(Level.toLevel(level));
            jqmlogger.info("Setting general log level at " + level + " which translates as log4j level " + Level.toLevel(level));
        }
        catch (Error e)
        {
            jqmlogger.warn("Log level could not be set", e);
        }
    }
}
