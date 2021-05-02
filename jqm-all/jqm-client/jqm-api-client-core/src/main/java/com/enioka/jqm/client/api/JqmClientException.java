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
package com.enioka.jqm.client.api;

/**
 * Denotes an internal error that happened inside the JQM API. It is not due to bad user input, but to configuration issues or bugs.
 */
public class JqmClientException extends JqmException
{
    private static final long serialVersionUID = 338795021501465434L;

    public JqmClientException(String message)
    {
        super(message);
    }

    public JqmClientException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public JqmClientException(Throwable cause)
    {
        super("an internal JQM client exception occured", cause);
    }
}
