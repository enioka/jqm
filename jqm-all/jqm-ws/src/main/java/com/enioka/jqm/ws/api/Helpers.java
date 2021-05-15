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

import java.util.Properties;

import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.DbManager;

public final class Helpers
{
    private static Db db = null;

    static
    {
        // Connect to DB.
        db = DbManager.getDb();

        Properties p = new Properties();
        p.put("com.enioka.jqm.jdbc.contextobject", db); // Share the DataSource in engine and client.
        JqmClientFactory.setProperties(p);
    }

    private Helpers()
    {
        // helper class
    }

    public static DbConn getDbSession()
    {
        return db.getConn();
    }

}
