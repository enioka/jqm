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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import com.enioka.jqm.api.client.core.JqmClientException;
import com.enioka.jqm.ws.api.ErrorDto;

//@Provider
public class JqmInternalExceptionMapper implements ExceptionMapper<JqmClientException>
{
    // @Context
    // private HttpHeaders headers;

    @Override
    public Response toResponse(JqmClientException exception)
    {
        // String type = headers.getMediaType() == null ? MediaType.APPLICATION_JSON :
        // headers.getMediaType().getType();
        ErrorDto d = new ErrorDto(exception.getMessage(), 9, exception, Status.INTERNAL_SERVER_ERROR);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(d).type(MediaType.APPLICATION_JSON).build();
    }
}
