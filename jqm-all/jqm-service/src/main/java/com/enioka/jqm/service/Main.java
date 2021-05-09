/**
 * Copyright © 2013 enioka. All rights reserved
 * Authors: Marc-Antoine GOUILLART (marc-antoine.gouillart@enioka.com)
 *          Pierre COPPEE (pierre.coppee@enioka.com)
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

package com.enioka.jqm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starter class & parameter parsing
 *
 */
public class Main
{
    private static Logger jqmlogger = LoggerFactory.getLogger(Main.class);

    private Main()
    {
        // Static class
    }

    /**
     * Windows service entry point for service start
     *
     * @param args
     */
    static void start(String[] args)
    {
        jqmlogger.info("Service start");
        main(args);
    }

    /**
     * Windows service entry point for service stop
     *
     * @param args
     */
    static void stop(String[] args)
    {
        jqmlogger.info("Service stop");
        // TODO
        // CommandStartNode.engine.stop();
    }

    /**
     * Startup method for the packaged JAR
     *
     * @param args
     *            0 is node name
     */
    public static void main(String[] args)
    {
        System.exit(OsgiRuntime.newFramework().runOsgiCommand(args));
    }

}
