package org.ligoj.app.plugin.build.travis;

import java.util.Map;

import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.app.resource.plugin.DefaultHttpResponseCallback;

/**
 * Travis processor.
 * 
 * @see https://docs.travis-ci.com/api/
 */
public class TravisCurlProcessor extends CurlProcessor {

	/**
	 * Token used to communicate with API.
	 */
	private String apiToken;

	/**
	 * Constructor using parameters set.
	 *
	 * @param parameters
	 *            the Travis parameters.
	 */
	public TravisCurlProcessor(final Map<String, String> parameters) {
		super(new DefaultHttpResponseCallback());
		this.apiToken = parameters.get("service:build:travis:api-token");
	}

	/**
	 * Process the given request.
	 */
	@Override
	protected boolean process(final CurlRequest request) {
		request.getHeaders().put("Authorization", "token " + this.apiToken);
		request.getHeaders().put("User-Agent", "Ligoj/1.0.0");
		request.getHeaders().put("Accept", "application/vnd.travis-ci.2+json");
		return super.process(request);
	}

}
