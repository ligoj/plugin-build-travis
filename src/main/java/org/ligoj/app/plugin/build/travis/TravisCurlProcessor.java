package org.ligoj.app.plugin.build.travis;

import java.util.Map;

import org.ligoj.app.resource.plugin.DefaultHttpResponseCallback;
import org.ligoj.app.resource.plugin.HttpResponseCallback;
import org.ligoj.app.resource.plugin.SessionAuthCurlProcessor;

/**
 * Jenkins processor.
 */
public class TravisCurlProcessor extends SessionAuthCurlProcessor {

	/**
	 * Constructor using parameters set.
	 * 
	 * @param parameters
	 *            the Jenkins parameters.
	 */
	public TravisCurlProcessor(final Map<String, String> parameters) {
		this(parameters, new DefaultHttpResponseCallback());
	}

	/**
	 * Constructor using parameters set and callback.
	 * 
	 * @param parameters
	 *            the Jenkins parameters.
	 * @param callback
	 *            Not <code>null</code> {@link HttpResponseCallback} used for each response.
	 */
	public TravisCurlProcessor(final Map<String, String> parameters, final HttpResponseCallback callback) {
		super(parameters.get(TravisPluginResource.PARAMETER_USER), parameters.get(TravisPluginResource.PARAMETER_TOKEN), callback);
	}

}
