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
package com.enioka.jqm.ws.plumbing;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import com.enioka.jqm.ws.api.ServiceAdmin;
import com.enioka.jqm.ws.api.ServiceClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JAX-RS application (not used in OSGi containers - for testing purposes) registering all JQM JAX-RS resources.
 */
@ApplicationPath("/ws/*")
public class JqmRestApp extends Application
{
    static Logger log = LoggerFactory.getLogger(JqmRestApp.class);

    @Override
    public Set<Class<?>> getClasses()
    {
        HashSet<Class<?>> res = new HashSet<>();

        // Load the APIs
        log.debug("\tRegistering admin service");
        res.add(ServiceAdmin.class);

        log.debug("\tRegistering client service");
        res.add(ServiceClient.class);

        // Load the exception mappers
        res.add(ErrorHandler.class);
        res.add(JqmExceptionMapper.class);
        res.add(JqmInternalExceptionMapper.class);

        // Logger
        res.add(ExceptionLogger.class);

        // Load the cache annotation helper
        res.add(HttpCacheImpl.class);

        return res;
    }

    public JqmRestApp()
    {
        log.debug("Starting REST WS app");
    }
}
