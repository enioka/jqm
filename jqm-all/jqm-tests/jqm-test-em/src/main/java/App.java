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

import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.log4j.Logger;

import com.enioka.jqm.api.JobBase;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;

@SuppressWarnings("deprecation")
public class App extends JobBase
{
    private static final Logger log = Logger.getLogger(App.class);

    @Override
    public void start()
    {
        log.info("Starting payload");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("marsu-pu");
        EntityManager em = emf.createEntityManager();

        log.info("Running query");
        em.createQuery("SELECT e from Entity e");

        if (this.getParameters().size() == 0)
        {
            // We use non-standard datasource names during tests
            Properties p = new Properties();
            String dbName = System.getenv("DB");
            dbName = (dbName == null ? "hsqldb" : dbName);
            p.put("javax.persistence.nonJtaDataSource", "jdbc/" + dbName);
            JqmClientFactory.setProperties(p);
            // End of datasource name change

            log.info("Queuing again - with parameter and through the full API");
            JqmClientFactory.getClient().newJobRequest("jqm-test-em", "marsu").addParameter("stop", "1").enqueue();
        }
        log.info("End of payload");
    }
}
