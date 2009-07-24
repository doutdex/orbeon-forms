/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.submission;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Regular remote submission going through a protocol handler.
 */
public class RegularSubmission extends BaseSubmission {

    public RegularSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return true;
    }

    public SubmissionResult connect(final PropertyContext propertyContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        final ExternalContext externalContext = getExternalContext(propertyContext);
        final URL absoluteResolvedURL = getResolvedSubmissionURL(propertyContext, externalContext, p2.resolvedActionOrResource, sp.queryString);

        // Gather remaining information to process the request
        final String forwardSubmissionHeaders = XFormsProperties.getForwardSubmissionHeaders(containingDocument);

        // NOTE about headers forwarding: forward user-agent header for replace="all", since that *usually*
        // simulates a request from the browser! Useful in particular when the target URL renders XForms
        // in noscript mode, where some browser sniffing takes place for handling the <button> vs. <submit>
        // element.
        final String newForwardSubmissionHeaders = p.isReplaceAll ? forwardSubmissionHeaders + " user-agent" : forwardSubmissionHeaders;

        // This will log more stuff
        final boolean logBody = XFormsModelSubmission.logger.isDebugEnabled();

        final IndentedLogger connectionLogger
                = new IndentedLogger(logBody ? XFormsModelSubmission.logger : XFormsServer.logger, "XForms submission " + (p2.isAsynchronous ? "(asynchronous)" : "(synchronous)"),
                    (p2.isAsynchronous && p.isReplaceNone) ? 1 : containingDocument.getIndentedLogger().getLogIndentLevel());

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(propertyContext, p.contextStack);

        final String submissionEffectiveId = submission.getEffectiveId();

        // Pack external call into a Runnable so it can be run:
        // o now and synchronously
        // o now and asynchronously
        // o later as a "foreground" asynchronous submission
        final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
            public SubmissionResult call() throws Exception {
                // Here we just want to run the submission and not touch the XFCD. Remember, we can't change XFCD
                // because it may get out of the caches and not be picked up by further incoming Ajax requests.

                // NOTE: If the submission was truly asynchronous, we should not touch ExternalContext either.
                // But currently, since the submission actually runs at the end of a request, we do have access to
                // ExternalContext, so we still use it.

                // Open the connection
                ConnectionResult connectionResult = null;
                try {
                    connectionResult = new Connection().open(externalContext, connectionLogger, logBody,
                        p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                        sp.actualRequestMediatype, sp.messageBody,
                        customHeaderNameValues, newForwardSubmissionHeaders);

                    // Obtain replacer
                    final Replacer replacer = submission.getReplacer(propertyContext, connectionResult, p);

                    // Deserialize here so it can run in parallel
                    replacer.deserialize(propertyContext, connectionResult, p, p2);

                    return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
                } catch (Throwable throwable) {
                    // Exceptions are handled further down
                    return new SubmissionResult(submissionEffectiveId, throwable, connectionResult);
                }
            }
        };

        // Submit the callable
        // This returns null if the execution is asynchronous
        return submitCallable(p, p2, callable);
    }
}
