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
package com.enioka.jqm.integration.tests;

import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.junit.Assert;
import org.junit.Test;

public class PackageTest extends JqmBaseTest
{
    @Test
    public void testPomOnlyInJar() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemavennopom/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testNoPomLibDir() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemavennopomlib/target/test.jar", TestHelpers.qVip,
                42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testLibInJar() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemavenjarinlib/target/test.jar", TestHelpers.qVip,
                42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testNoDependencyDefinition() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemavennolibdef/target/test.jar", TestHelpers.qVip,
                42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testInheritedLegacyPayload() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.PckJBInheritance").run(this);
    }
}
