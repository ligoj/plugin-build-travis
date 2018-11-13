package org.ligoj.app.plugin.build.travis;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.build.BuildResource;
import org.ligoj.app.plugin.build.BuildServicePlugin;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Travis CI resource.
 */
@Path(TravisPluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
public class TravisPluginResource extends AbstractToolPluginResource implements BuildServicePlugin {

	/**
	 * Plug-in key.
	 */
	public static final String URL = BuildResource.SERVICE_URL + "/travis";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Travis user name able to connect to instance.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Travis user api-token able to connect to instance.
	 */
	public static final String PARAMETER_TOKEN = KEY + ":api-token";

	/**
	 * Travis job's name.
	 */
	public static final String PARAMETER_JOB = KEY + ":job";

	/**
	 * Travis job's name.
	 */
	public static final String PARAMETER_TEMPLATE_JOB = KEY + ":template-job";

	/**
	 * Web site URL
	 */
	public static final String PARAMETER_URL = KEY + ":url-api";

	/**
	 * Travis code to status color. Default color is "red".
	 */
	private static final Map<String, String> CODE_TO_STATUS = new HashMap<>();

	static {
		CODE_TO_STATUS.put("passed", "blue");
		CODE_TO_STATUS.put("started", "yellow");
	}

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public void link(final int subscription) throws Exception {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		// Validate the job settings
		validateJob(parameters);
	}

	/**
	 * Validate the administration connectivity.
	 *
	 * @param parameters
	 *            the administration parameters.
	 * @return job name.
	 */
	protected Job validateJob(final Map<String, String> parameters) throws URISyntaxException, IOException {
		// Get job's configuration
		final String job = parameters.get(PARAMETER_JOB);
		String jobJson = getResource(parameters, "/repos/" + encode(job));
		if (jobJson == null) {
			// Invalid couple PKEY and id
			throw new ValidationJsonException(PARAMETER_JOB, "travis-job", job);
		}

		final JsonNode node = objectMapper.readTree(jobJson);

		// Retrieve description, status and display name
		return transform(node.get("repo"));
	}

	private String encode(final String job) throws MalformedURLException, URISyntaxException {
		return new URI("http", job, "").toURL().getPath();
	}

	/**
	 * Return a Jenkins's resource. Return <code>null</code> when the resource
	 * is not found.
	 */
	protected String getResource(final Map<String, String> parameters, final String resource) {
		return getResource(new TravisCurlProcessor(parameters), parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a Jenkins's resource. Return <code>null</code> when the resource
	 * is not found.
	 */
	protected String getResource(final CurlProcessor processor, final String url, final String resource) {
		// Get the resource using the preempted authentication
		final CurlRequest request = new CurlRequest("GET", StringUtils.appendIfMissing(url, "/") + resource, null);
		request.setSaveResponse(true);
		processor.process(request);
		// TODO Handle 403 response with ligoj-api 1.1.9+
		return request.getResponse();
	}

	/**
	 * Search the Travis's jobs matching to the given criteria. Name, display
	 * name and description are considered.
	 *
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param criteria
	 *            the search criteria.
	 * @return job names matching the criteria.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<Job> findAllByName(@PathParam("node") final String node, @PathParam("criteria") final String criteria) throws IOException {
		return findAllByName(node, criteria, null);
	}

	/**
	 * Get Travis job name by id.
	 *
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param id
	 *            The job name/identifier.
	 * @return job names matching the criteria.
	 */
	@GET
	@Path("{node}/job/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Job findById(@PathParam("node") final String node, @PathParam("id") final String id) throws URISyntaxException, IOException {
		// Prepare the context, an ordered set of jobs
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		parameters.put(PARAMETER_JOB, id);
		return validateJob(parameters);
	}

	/**
	 * Search the Jenkin's jobs matching to the given criteria. Name, display
	 * name and description are considered.
	 *
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param criteria
	 *            the search criteria.
	 * @param view
	 *            The optional view URL.
	 * @return job names matching the criteria.
	 */
	private List<Job> findAllByName(final String node, final String criteria, final String view) throws IOException {
		final Map<String, String> parameters = pvResource.getNodeParameters(node);

		// Get the jobs and parse them
		final String url = StringUtils.trimToEmpty(view) + "repos?search=" + criteria + "&orderBy=name&limit=10";
		final InputStream jobsAsInput = IOUtils.toInputStream(StringUtils.defaultString(getResource(parameters, url), "{\"repos\":[]}"),
				StandardCharsets.UTF_8);
		final JsonNode jsonNode = objectMapper.readTree(jobsAsInput);
		final ArrayNode jobsNode = (ArrayNode) jsonNode.get("repos");
		return StreamSupport.stream(jobsNode.spliterator(), false).map(TravisPluginResource::transform).collect(Collectors.toList());
	}

	/**
	 * Transform the json content to an instance of <tt>Job</tt>.
	 *
	 * @param item
	 *            Json Content describing a job
	 * @return Instance of <tt>Job</tt>
	 */
	private static Job transform(JsonNode item) {
		Job result = new Job();
		result.setName(item.get("slug").asText());
		result.setDescription(item.get("description").asText());
		final String statusNode = StringUtils.defaultString(item.get("last_build_state").asText(), "red");
		result.setStatus(toStatus(statusNode));
		result.setLastBuildId((item.get("last_build_id").asText(null)));
		result.setBuilding("started".equals(statusNode));
		result.setId(item.get("slug").asText());
		return result;
	}

	/**
	 * Return the color from the status of the job.
	 *
	 * @param status
	 *            last status for the job
	 * @return The color for the current status.
	 */
	private static String toStatus(final String status) {
		return CODE_TO_STATUS.getOrDefault(status, "red");
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Try to obtain the configuration
		return getResource(parameters, "config") != null;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws Exception {
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		nodeStatusWithData.put("job", validateJob(parameters));
		return nodeStatusWithData;
	}

	/**
	 * Used to launch the job for the subscription.
	 *
	 * @param subscription
	 *            the subscription to use to locate the Travis instance.
	 * @throws Exception
	 *             when the job cannot be launched
	 */
	@POST
	@Path("build/{subscription:\\d+}")
	public void build(@PathParam("subscription") final int subscription) throws URISyntaxException, IOException {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		try {
			// Check the instance is available
			final Job job = validateJob(parameters);

			if (job.getLastBuildId() == null || !build(parameters, job)) {
				throw new BusinessException("Launching the job for the subscription {} failed.", subscription);
			}
		} catch (ValidationJsonException e) {
			throw new BusinessException("Launching the job for the subscription {} failed.", subscription);
		}
	}

	/**
	 * Launch the job with the job.
	 *
	 * @param parameters
	 *            Parameters used to define the job
	 * @param job
	 *            contains some information on the job as the last build id.
	 * @return The result of the processing.
	 */
	protected boolean build(final Map<String, String> parameters, final Job job) {
		final CurlProcessor processor = new TravisCurlProcessor(parameters);
		try {
			final String travisBaseUrl = parameters.get(PARAMETER_URL);
			return processor.process(new CurlRequest("POST", travisBaseUrl + "/builds/" + job.getLastBuildId() + "/restart", null));
		} finally {
			processor.close();
		}
	}

}
