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
package com.enioka.jqm.ws.plumbing.shiro;

import com.enioka.jqm.ws.api.Helpers;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.model.RPermission;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.RUser;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.codec.Hex;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

public class JdbcRealm extends AuthorizingRealm
{
    static Logger log = LoggerFactory.getLogger(JdbcRealm.class);

    public JdbcRealm()
    {
        setName("database");
    }

    @Override
    protected void assertCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) throws AuthenticationException
    {
        if (token instanceof CertificateToken)
        {
            if (!((CertificateToken) token).getUserName().equals(info.getPrincipals().getPrimaryPrincipal()))
            {
                log.warn("certificate [{}] presented did not match the awaited username: expected [{}] got [{}]", token.getPrincipal(),
                        info.getPrincipals().getPrimaryPrincipal(), ((CertificateToken) token).getUserName());
                throw new IncorrectCredentialsException("certificate presented did not match the awaited username");
            }
            return;
        }
        super.assertCredentialsMatch(token, info);
    }

    @Override
    public boolean supports(AuthenticationToken token)
    {
        return (token instanceof UsernamePasswordToken) || (token instanceof CertificateToken);
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals)
    {
        return getUser((String) principals.fromRealm(getName()).iterator().next());
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException
    {
        if (token instanceof CertificateToken)
        {
            log.debug("using certificate [{}] to authenticate user [{}]", token.getPrincipal(), ((CertificateToken) token).getUserName());
            return getUser(((CertificateToken) token).getUserName());
        }
        UsernamePasswordToken t = (UsernamePasswordToken) token;
        return getUser(t.getUsername());
    }

    private SimpleAccount getUser(String login)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            RUser user = RUser.selectlogin(cnx, login);

            // Credential is a password - in token, it is as a char array
            SimpleAccount res = new SimpleAccount(user.getLogin(), user.getPassword(), getName());

            if (user.getExpirationDate() != null)
            {
                res.setCredentialsExpired(user.getExpirationDate().before(Calendar.getInstance()));
            }
            else
            {
                // No limit = never expires
                res.setCredentialsExpired(false);
            }
            if (user.getHashSalt() != null)
            {
                res.setCredentialsSalt(ByteSource.Util.bytes(Hex.decode(user.getHashSalt())));
            }
            else
            {
                res.setCredentialsSalt(null);
            }
            res.setLocked(user.getLocked());

            // Roles
            for (RRole r : user.getRoles(cnx))
            {
                res.addRole(r.getName());
                for (RPermission p : r.getPermissions(cnx))
                {
                    res.addStringPermission(p.getName());
                }
            }
            if (!user.getInternal())
            {
                res.addRole("human");
            }
            return res;
        }
        catch (NoResultException e)
        {
            // No such user in realm
            log.warn("no such user found [{}]", login);
            return null;
        }
        catch (RuntimeException e)
        {
            log.error("Could not retrieve user from database", e);
            throw e;
        }
    }

}
