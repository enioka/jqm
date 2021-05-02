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
package com.enioka.jqm.client.shared;

import java.util.Calendar;
import java.util.UUID;

import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha512Hash;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.RUser;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 *
 * Helper class helping dealing with internal security when calling the simple REST API (the API dealing with file transfers)<br>
 * This API may be secured - therefore, we must create a temporary internal user with the necessary permissions.
 *
 */
public final class SimpleApiSecurity
{
    private static Logger jqmlogger = LoggerFactory.getLogger(SimpleApiSecurity.class);

    private static volatile RUser user;
    private static volatile String secret;
    private static volatile Duet logindata;
    private static Boolean useAuth = null;
    private static volatile Object lock = new Object();

    public static class Duet
    {
        public String usr;
        public String pass;
    }

    private SimpleApiSecurity()
    {
        // Helper static class
    }

    /**
     * Will create (or recreate) if necessary the temporary login data.<br>
     * Will create its own transaction - therefore the given connection must not have any active transaction.
     */
    public static Duet getId(DbConn cnx)
    {
        Duet currentLoginData = logindata; // Pointer copy to avoid having it reset under us during this method run.
        RUser currentUser = user;

        if (useAuth == null)
        {
            useAuth = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableWsApiAuth", "true"));
        }

        if (currentLoginData == null)
        {
            currentLoginData = new Duet();
            currentLoginData.pass = null;
            currentLoginData.usr = null;
            logindata = currentLoginData;
        }

        if (!useAuth)
        {
            jqmlogger.debug("The client web API calls will not use any authentication");
            return currentLoginData;
        }

        // Renew one day before limit, to ensure account is valid for a day.
        Calendar renewalLimit = null;
        if (currentUser != null)
        {
            renewalLimit = (Calendar) currentUser.getExpirationDate().clone(); // clone. otherwise we modify the user object next line!
            renewalLimit.add(Calendar.DAY_OF_YEAR, -1);
        }

        if (currentUser == null || renewalLimit == null || renewalLimit.before(Calendar.getInstance()))
        {
            synchronized (lock)
            {
                if (currentUser == null || currentUser.getExpirationDate().before(Calendar.getInstance()))
                {
                    jqmlogger.debug("The client API will create an internal secret to access the simple API");

                    // Create new
                    String login = UUID.randomUUID().toString();

                    secret = UUID.randomUUID().toString();
                    ByteSource salt = new SecureRandomNumberGenerator().nextBytes();
                    String hash = new Sha512Hash(secret, salt, 100000).toHex();
                    String saltS = salt.toHex();

                    Calendar expiration = Calendar.getInstance();
                    expiration.add(Calendar.DAY_OF_YEAR, 2);

                    int id = RUser.create(cnx, login, hash, saltS, expiration, true, "administrator");
                    currentUser = RUser.select_id(cnx, id);
                    user = currentUser;

                    currentLoginData = new Duet();
                    currentLoginData.pass = secret;
                    currentLoginData.usr = login;
                    logindata = currentLoginData;

                    // Purge all old internal accounts
                    cnx.runUpdate("user_delete_expired_internal");

                    cnx.commit();
                }
            }
        }

        return currentLoginData;
    }

    public static void dispose()
    {
        user = null;
        logindata = null;
        secret = null;
        useAuth = null;
    }
}
