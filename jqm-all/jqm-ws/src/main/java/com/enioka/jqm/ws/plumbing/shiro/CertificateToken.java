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

import org.apache.shiro.authc.AuthenticationToken;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class CertificateToken implements AuthenticationToken
{
    private static final long serialVersionUID = -3513329844423322673L;

    protected X509Certificate clientCert;

    public CertificateToken(X509Certificate c)
    {
        this.clientCert = c;
    }

    @Override
    public Object getPrincipal()
    {
        return clientCert.getSubjectDN().getName();
    }

    public String getUserName()
    {
        try
        {
            X500Name x500name = new JcaX509CertificateHolder(clientCert).getSubject();
            RDN cn = x500name.getRDNs(BCStyle.CN)[0];
            return IETFUtils.valueToString(cn.getFirst().getValue());
        }
        catch (CertificateEncodingException e)
        {
            return "";
        }
    }

    @Override
    public Object getCredentials()
    {
        // No need for credentials - the very existence of a validated certificate is
        // enough
        return null;
    }

    public X509Certificate getClientCert()
    {
        return clientCert;
    }
}
