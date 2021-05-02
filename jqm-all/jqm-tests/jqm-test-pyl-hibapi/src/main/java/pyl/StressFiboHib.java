package pyl;

import java.util.Properties;

import com.enioka.jqm.api.JobManager;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;

public class StressFiboHib implements Runnable
{
    JobManager jm;

    @Override
    public void run()
    {
        System.out.println("PARAMETRE FIBO 2: " + jm.parameters().get("p2"));

        // We use non-standard datasource names during tests
        Properties p = new Properties();
        String dbName = System.getenv("DB");
        dbName = (dbName == null ? "hsqldb" : dbName);
        p.put("javax.persistence.nonJtaDataSource", "jdbc/" + dbName);
        JqmClientFactory.setProperties(p);
        // End of datasource name change

        if (Integer.parseInt(jm.parameters().get("p1")) <= 100)
        {
            System.out.println("BEFORE ENQUEUE");
            JqmClientFactory.getClient().newJobRequest(jm.applicationName(), jm.userName()).addParameter("p1", jm.parameters().get("p2"))
                    .addParameter("p2", "" + (Integer.parseInt(jm.parameters().get("p2")) + Integer.parseInt(jm.parameters().get("p1"))))
                    .enqueue();
        }
        System.out.println("QUIT FIBO");
    }
}
