package com.enioka.jqm.integration.tests;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;
import com.enioka.jqm.xml.JqmXmlException;
import com.enioka.jqm.xml.XmlJobDefParser;

public class SpringTest extends JqmBaseTest
{
    @Before
    public void prepare()
    {
        FileUtils.deleteQuietly(new File("./target/TEST.db"));
    }

    /**
     * No specific CL - isolated launch, using normal runners. Still, all launches work on the same database.
     */
    @Test
    public void testSimpleSingleLaunch()
    {
        String ver = System.getProperty("java.version");
        double javaVersion = Double.parseDouble(ver.substring(0, ver.indexOf('.') + 2));
        Assume.assumeTrue(javaVersion < 1.9); // The tested version of Spring is not really compatible with Java 9+.

        CreationTools.createDatabaseProp("jdbc/spring_ds", "org.h2.Driver", "jdbc:h2:./target/TEST.db;DB_CLOSE_ON_EXIT=FALSE", "sa", "sa",
                cnx, "SELECT 1", null, true);
        CreationTools.createJobDef(null, true, "com.enioka.jqm.test.spring1.Application", null,
                "jqm-tests/jqm-test-spring-1/target/test.jar", TestHelpers.qVip, -1, "TestSpring1", null, null, null, null, null, false,
                cnx, null);

        addAndStartEngine();

        // First job creates the database, so let it finish (test artifact).
        jqmClient.newJobRequest("TestSpring1", null).enqueue();
        TestHelpers.waitFor(1, 30000, cnx);

        jqmClient.newJobRequest("TestSpring1", null).enqueue();
        jqmClient.newJobRequest("TestSpring1", null).enqueue();

        TestHelpers.waitFor(3, 60000, cnx);

        Assert.assertEquals(3, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    /**
     * Using the Spring runner and an XML job def. Actually uses test-spring-2.
     */
    @Test
    public void testSpringRunner()
    {
        String ver = System.getProperty("java.version");
        double javaVersion = Double.parseDouble(ver.substring(0, ver.indexOf('.') + 2));
        Assume.assumeTrue(javaVersion < 1.9); // The tested version of Spring is not really compatible with Java 9+.

        try
        {
            XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmlspring.xml", cnx);
        }
        catch (JqmXmlException e)
        {
            jqmlogger.error("could not parse XML", e);
            Assert.fail();
        }

        jqmClient.newJobRequest("Job1", null).addParameter("key1", "value1").enqueue();
        addAndStartEngine();

        TestHelpers.waitFor(1, 10000, cnx);
        jqmClient.newJobRequest("Job2", null).addParameter("key1", "valueKey1FromRequestNotDefinition").enqueue();

        TestHelpers.waitFor(3, 10000, cnx);
        Assert.assertEquals(3, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }
}
