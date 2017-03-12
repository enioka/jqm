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

package com.enioka.jqm.jpamodel;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DbConn;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 * Persistence class for storing the default parameters of a {@link JobDef}, i.e. key/value pairs that should be present for all instances
 * created from a JobDef (and may be overloaded).<br>
 * When a {@link JobDef} is instantiated, {@link RuntimeParameter}s are created from {@link JobDefParameter}s as well as parameters
 * specified inside the execution request and associated to the {@link JobInstance}. Therefore, this table is purely metadata and is never
 * used in TP processing.
 */
public class JobDefParameter implements Serializable
{
    private static final long serialVersionUID = -5308516206913425230L;

    private Integer id;

    private String key;
    private String value;

    private int jobdef_id;

    /**
     * The name of the parameter.<br>
     * Max length is 50.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * See {@link #getKey()}
     */
    public void setKey(final String key)
    {
        this.key = key;
    }

    /**
     * Value of the parameter.<br>
     * Max length is 1000.
     */
    public String getValue()
    {
        return value;
    }

    /**
     * See {@link #getValue()}
     */
    public void setValue(final String value)
    {
        this.value = value;
    }

    /**
     * A technical ID without special meaning.
     */
    public Integer getId()
    {
        return id;
    }

    public static List<JobDefParameter> select(DbConn cnx, String query_key, Object... args)
    {
        List<JobDefParameter> res = new ArrayList<JobDefParameter>();
        try
        {
            ResultSet rs = cnx.runSelect(query_key, args);
            while (rs.next())
            {
                JobDefParameter tmp = new JobDefParameter();

                tmp.id = rs.getInt(0);
                tmp.key = rs.getString(1);
                tmp.value = rs.getString(2);
                tmp.jobdef_id = rs.getInt(3);

                res.add(tmp);
            }
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }
}
