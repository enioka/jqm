package com.enioka.jqm.cli;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;

@Parameters(commandNames = "Set-WebConfiguration", commandDescription = "Changes the way JQM exposes its web services and GUI.")
class CommandSetWebConfiguration extends CommandBase
{
    @Parameter(names = { "-c",
            "--change" }, description = "(ENABLE_HTTP_GUI|DISABLE_ALL|ENABLE_TLS|DISABLE_TLS|ENABLE_INTERNAL_PKI|DISABLE_INTERNAL_PKI|ENABLE_AUTHENTICATION|DISABLE_AUTHENTICATION)", required = true, converter = EnumConverter.class)
    private Action action;

    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            switch (action)
            {
            case ENABLE_HTTP_GUI:
                Helpers.setSingleParam("disableWsApi", "false", cnx);
                Helpers.setSingleParam("enableWsApiSsl", "false", cnx);
                Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
                Helpers.setSingleParam("disableWsApiSimple", "false", cnx);
                Helpers.setSingleParam("disableWsApiClient", "false", cnx);
                Helpers.setSingleParam("disableWsApiAdmin", "false", cnx);
                Helpers.setSingleParam("enableInternalPki", "true", cnx);

                cnx.runUpdate("node_update_all_enable_ws");
                cnx.commit();
                break;
            case DISABLE_ALL:
                Helpers.setSingleParam("disableWsApi", "true", cnx);
                cnx.runUpdate("node_update_all_disable_ws");
                cnx.commit();
                break;
            case ENABLE_TLS:
                Helpers.setSingleParam("enableWsApiSsl", "true", cnx);
                break;
            case DISABLE_TLS:
                Helpers.setSingleParam("enableWsApiSsl", "false", cnx);
                break;
            case ENABLE_INTERNAL_PKI:
                Helpers.setSingleParam("enableInternalPki", "true", cnx);
                break;
            case DISABLE_INTERNAL_PKI:
                Helpers.setSingleParam("enableInternalPki", "false", cnx);
                break;
            case ENABLE_AUTHENTICATION:
                Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
                break;
            case DISABLE_AUTHENTICATION:
                Helpers.setSingleParam("enableWsApiAuth", "false", cnx);
                break;
            }
        }
        return 0;
    }

    enum Action {
        /**
         * Changes all configuration options needed to enable the web UI on HTTP (not HTTPS).
         */
        ENABLE_HTTP_GUI,
        /**
         * Disable everything web related - JQM will no open any socket for web-related operations.
         */
        DISABLE_ALL,
        /**
         * Ask JQM to use HTTP and not HTTPS on all its web operations.
         */
        ENABLE_TLS,
        /**
         * Ask JQM to use HTTPS and not HTTP on all its web operations.
         */
        DISABLE_TLS,
        /**
         * Use a JQM-provided PKI for all certificate needs.
         */
        ENABLE_INTERNAL_PKI,
        /**
         * Use an external PKI for all certificate needs. It must be configured if using TLS.
         */
        DISABLE_INTERNAL_PKI, ENABLE_AUTHENTICATION, DISABLE_AUTHENTICATION
    }
}

class EnumConverter implements IStringConverter<CommandSetWebConfiguration.Action>
{
    @Override
    public CommandSetWebConfiguration.Action convert(String value)
    {
        CommandSetWebConfiguration.Action convertedValue = CommandSetWebConfiguration.Action.valueOf(value);

        if (convertedValue == null)
        {
            throw new ParameterException("Value " + value + "can not be converted to Action. ");
        }
        return convertedValue;
    }
}
