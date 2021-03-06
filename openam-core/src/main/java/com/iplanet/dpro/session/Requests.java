/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

package com.iplanet.dpro.session;

import java.net.URL;
import java.security.AccessController;

import javax.inject.Inject;

import org.forgerock.openam.session.SessionPLLSender;
import org.forgerock.openam.session.service.ServicesClusterMonitorHandler;

import com.iplanet.am.util.SystemProperties;
import com.iplanet.dpro.session.monitoring.ForeignSessionHandler;
import com.iplanet.dpro.session.share.SessionRequest;
import com.iplanet.dpro.session.share.SessionResponse;
import com.iplanet.sso.SSOToken;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.session.util.RestrictedTokenContext;

/**
 * Responsible for performing the Session based logic of sending a request.
 *
 * The remote operations performed by this class are driven around a Request/Response
 * model where the generic Request is assigned values which indicate the type of request
 * being made.
 *
 * The response accordingly is generic and needs to be inspected for the required return
 * values from the request.
 *
 */
public class Requests {

    private final ServicesClusterMonitorHandler servicesClusterMonitorHandler;
    private final ForeignSessionHandler foreignSessionHandler;
    private final SessionPLLSender pllSender;

    @Inject
    public Requests(
                    final ServicesClusterMonitorHandler servicesClusterMonitorHandler,
                    final ForeignSessionHandler foreignSessionHandler,
                    final SessionPLLSender pllSender) {
        this.servicesClusterMonitorHandler = servicesClusterMonitorHandler;
        this.foreignSessionHandler = foreignSessionHandler;
        this.pllSender = pllSender;
    }

    /**
     * When used in internal request routing mode, it sends remote session
     * request with retries. If not in internal request routing mode simply
     * calls <code>getSessionResponseWithRetry</code>.
     *
     * @param svcurl Session Service URL.
     * @param sreq Session Request object.
     * @exception SessionException
     *
     */
    public SessionResponse sendRequestWithRetry(URL svcurl, SessionRequest sreq, Session session)
            throws SessionException {
        if (SystemProperties.isServerMode()) {

            try {
                return getSessionResponseWithRetry(svcurl, sreq, session);
            } catch (SessionException e) {
                // attempt retry if appropriate
                String hostServer = foreignSessionHandler.getCurrentHostServer(session.getID());
                if (!servicesClusterMonitorHandler.checkServerUp(hostServer)) {
                    // proceed with retry
                    // Note that there is a small risk of repeating request
                    // twice (e.g., normal exception followed by server failure)
                    // This danger is insignificant because most of our requests
                    // are idempotent. For those which are not (e.g.,
                    // logout/destroy)
                    // it is not critical if we get an exception attempting to
                    // repeat this type of request again.

                    URL retryURL = session.getSessionServiceURL();
                    if (!retryURL.equals(svcurl)) {
                        return getSessionResponseWithRetry(retryURL, sreq, session);
                    }
                }
                throw e;
            }
        } else {
            return getSessionResponseWithRetry(svcurl, sreq, session);
        }
    }

    /**
     * Sends remote session request without retries.
     *
     * @param svcurl Session Service URL.
     * @param sreq Session Request object.
     * @exception SessionException
     */
    public SessionResponse getSessionResponseWithRetry(URL svcurl, SessionRequest sreq, Session session) throws SessionException {
        SessionResponse sres;
        Object context = RestrictedTokenContext.getCurrent();

        SSOToken appSSOToken = null;
        // Client side non-authentication request which does not already have a context
        if (!SystemProperties.isServerMode() && !session.getID().getComingFromAuth() && context == null) {
            appSSOToken = AccessController.doPrivileged(AdminTokenAction.getInstance());
            session.createContext(appSSOToken);
            context = session.getContext();
        }
        
        try {
            if (context != null) {
                sreq.setRequester(RestrictedTokenContext.marshal(context));
            }
            sres = pllSender.sendPLLRequest(svcurl, sreq);
            while (sres.getException() != null) {
                session.processSessionResponseException(sres, appSSOToken);
                context = session.getContext();
                if (context != null) {
                    sreq.setRequester(RestrictedTokenContext.marshal(context));
                }
                // send request again
                sres = pllSender.sendPLLRequest(svcurl, sreq);
            }
        } catch (Exception e) {
            throw new SessionException(e);
        }

        return sres;
    }
}
