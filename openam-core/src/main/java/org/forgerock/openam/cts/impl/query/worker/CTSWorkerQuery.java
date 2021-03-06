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
package org.forgerock.openam.cts.impl.query.worker;

import java.util.Collection;

import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.cts.worker.CTSWorkerInit;
import org.forgerock.openam.cts.worker.CTSWorkerTask;
import org.forgerock.openam.tokens.CoreTokenField;

/**
 * Defines the ability to perform a paged query. This will be used by a {@link CTSWorkerTask} to
 * identify all CTS tokens which will be processed by the task.
 *
 * @see CTSWorkerInit
 */
public interface CTSWorkerQuery {

    /**
     * Repeated calls will return further results from query.
     *
     * @return Non null, non empty collection of {@link CoreTokenField#TOKEN_ID} to be
     * processed by the {@link CTSWorkerTask}. Null indicates there are no more Tokens to process.
     *
     * @throws CoreTokenException If there was any unexpected error during processing.
     */
    Collection<String> nextPage() throws CoreTokenException;
}
