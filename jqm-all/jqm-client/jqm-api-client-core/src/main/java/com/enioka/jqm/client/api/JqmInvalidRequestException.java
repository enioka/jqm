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
 * Denotes an input error from the user of the API. The message gives the detail of his error.
 */
public class JqmInvalidRequestException extends JqmException
{
    private static final long serialVersionUID = 2248971878792826983L;

    public JqmInvalidRequestException(String msg, Exception e)
    {
        super(msg, e);
    }

    public JqmInvalidRequestException(String msg)
    {
        super(msg);
    }
}
