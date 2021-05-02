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

package com.enioka.jqm.client.api;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A job instance queue. Job request (i.e. job instances) are put inside queues. There are different queues for different uses depending on
 * the environment.
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Queue implements Serializable
{
    private static final long serialVersionUID = 5730264060976148489L;

    private int id;
    private String name;
    private String description;

    /**
     * Each queue has a unique ID
     */
    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    /**
     * Name of the queue, usually descriptive.
     */
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * Queue description.
     */
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
}
