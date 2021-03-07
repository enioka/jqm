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
package com.enioka.jqm.ws.api;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.ws.rs.core.Response.Status;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ErrorDto extends RuntimeException
{
    private static final long serialVersionUID = 6661530066660670253L;

    private String userReadableMessage, developerMessage, stacktrace;
    private Integer errorCode;

    public Status httpStatus;

    public ErrorDto()
    {
        super();
    }

    public ErrorDto(String userReadableMessage, String devMessage, Integer errorCode, Status status)
    {
        this.userReadableMessage = userReadableMessage;
        this.httpStatus = status;
        this.developerMessage = devMessage;
        this.errorCode = errorCode;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < trace.length; i++)
            pw.println("\tat " + trace[i]);
        this.stacktrace = sw.toString();
    }

    public ErrorDto(String userReadableMessage, Integer errorCode, Exception e, Status status)
    {
        this.userReadableMessage = userReadableMessage;
        this.httpStatus = status;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        sw.toString();
        this.stacktrace = sw.toString();
        this.errorCode = errorCode;
    }

    public String getUserReadableMessage()
    {
        return userReadableMessage;
    }

    public void setUserReadableMessage(String userReadableMessage)
    {
        this.userReadableMessage = userReadableMessage;
    }

    public String getDeveloperMessage()
    {
        return developerMessage;
    }

    public void setDeveloperMessage(String developerMessage)
    {
        this.developerMessage = developerMessage;
    }

    public Integer getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode)
    {
        this.errorCode = errorCode;
    }

    public String getStacktrace()
    {
        return stacktrace;
    }

    public void setStacktrace(String stacktrace)
    {
        this.stacktrace = stacktrace;
    }
}
