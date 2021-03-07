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

import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import com.enioka.jqm.ws.api.ServiceAdmin;
import com.enioka.jqm.ws.api.ServiceClient;
import com.enioka.jqm.ws.api.ServiceSimple;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationBase;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@Component(service = Application.class, scope = ServiceScope.SINGLETON)
@JaxrsApplicationBase("ws")
@JaxrsName("JQMWS")
public class JqmRestAppOsgi extends Application {
    static Logger log = LoggerFactory.getLogger(JqmRestAppOsgi.class);

    @Override
    public Set<Class<?>> getClasses() {
        HashSet<Class<?>> res = new HashSet<>();

        // TODO get node id.

        // Determine which of the three APIs should be loaded
        boolean loadApiSimple = true;
        boolean loadApiClient = true;
        boolean loadApiAdmin = true;

        // Load the APIs
        if (loadApiAdmin) {
            log.debug("\tRegistering admin service");
            res.add(ServiceAdmin.class);
        }
        if (loadApiClient) {
            log.debug("\tRegistering client service");
            res.add(ServiceClient.class);
        }
        if (loadApiSimple) {
            log.debug("\tRegistering simple service");
            res.add(ServiceSimple.class);
        }

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

    public JqmRestAppOsgi() {
        log.debug("Starting REST OSGi WS app");
    }
}
