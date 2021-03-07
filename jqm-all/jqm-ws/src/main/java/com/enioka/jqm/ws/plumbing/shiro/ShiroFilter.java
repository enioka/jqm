/**
 * Copyright © 2013 enioka. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enioka.jqm.ws.plumbing.shiro;

import com.enioka.jqm.ws.api.Helpers;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter extends the usual ShiroFilter by checking in the database if security should be enabled or not.
 *
 */
public class ShiroFilter extends org.apache.shiro.web.servlet.ShiroFilter
{
    private static Logger jqmlogger = LoggerFactory.getLogger(ShiroFilter.class);

    @Override
    public void init() throws Exception
    {
        String enableWsApiAuth = this.getContextInitParam("enableWsApiAuth");
        boolean load = true;

        if (enableWsApiAuth != null)
        {
            // Self-hosted mode: parameter is given by the server
            load = Boolean.parseBoolean(enableWsApiAuth);
        }
        else
        {
            // Hosted in a standard servlet container - fetch from db.
            try (DbConn cnx = Helpers.getDbSession())
            {
                load = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableWsApiAuth", "true"));
            }
        }

        jqmlogger.debug("Shiro filter enabled: " + load + " - " + enableWsApiAuth);
        setEnabled(load);
        super.init();
    }
}
