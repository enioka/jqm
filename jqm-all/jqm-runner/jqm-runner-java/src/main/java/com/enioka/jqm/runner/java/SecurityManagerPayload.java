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
package com.enioka.jqm.runner.java;

import java.security.Permission;

/**
 * A security manager ensuring a minimal good behavior for Java payloads running inside the JQM engine.
 */
public class SecurityManagerPayload extends SecurityManager
{
    /**
     * Default is: everything is allowed.
     */
    @Override
    public void checkPermission(Permission perm)
    {
        // Not throwing SecurityException = allowed.
    }

    @Override
    public void checkPermission(Permission perm, Object context)
    {
        // Default implementation for payloads, no checks for inner code.
        if (isPayloadRequest())
        {
            // Allow JMX registration, whatever the JVM defaults say.
            if (perm.toString().equals("(\"javax.management.MBeanTrustPermission\" \"register\")"))
            {
                return;
            }

            // Default case: apply JVM default.
            super.checkPermission(perm, context);
        }
        else
        {
            return;
        }
    }

    /**
     * Ensures payloads are not allowed to call <code>System.exit()</code>
     */
    @Override
    public void checkExit(int status)
    {
        if (isPayloadRequest())
        {
            throw new SecurityException("JQM payloads cannot call System.exit() - this would stop JQM itself!");
        }
    }

    private boolean isPayloadRequest()
    {
        // return Thread.currentThread().getName().contains(";payload;");
        return Thread.currentThread().getContextClassLoader() instanceof PayloadClassLoader;
    }
}
