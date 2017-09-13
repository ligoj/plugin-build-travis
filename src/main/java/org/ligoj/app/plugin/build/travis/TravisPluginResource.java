package org.ligoj.app.plugin.build.travis;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.build.BuildResource;
import org.ligoj.app.plugin.build.BuildServicePlugin;
import org.ligoj.app.resource.plugin.AbstractXmlApiToolPluginResource;
import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.app.resource.plugin.HeaderHttpResponseCallback;
import org.ligoj.app.resource.plugin.OnlyRedirectHttpResponseCallback;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class TravisPluginResource extends AbstractXmlApiToolPluginResource implements BuildServicePlugin {

	/**
	 * Public server URL used to fetch the last available version of the
	 * product.
	 */
	@Value("${service-build-jenkins-server:http://mirrors.jenkins-ci.org}")
	private String publicServer;

	@Autowired
	protected IamProvider[] iamProvider;

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
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * Travis version callback to extract the header.
	 */
	private static final HeaderHttpResponseCallback VERSION_CALLBACK = new HeaderHttpResponseCallback("x-jenkins");

	@Override
	public void link(final int subscription) throws Exception {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the node settings
		validateAdminAccess(parameters);

		// Validate the job settings
		validateJob(parameters);
	}

	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) throws Exception {
		if (deleteRemoteData) {
			final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
			// Validate the node settings
			validateAdminAccess(parameters);

			// delete the job
			final String job = parameters.get(PARAMETER_JOB);
			final String jenkinsBaseUrl = parameters.get(PARAMETER_URL);
			final CurlRequest curlRequest = new CurlRequest(HttpMethod.POST,
					jenkinsBaseUrl + "/job/" + encode(job) + "/doDelete", StringUtils.EMPTY);
			if (!new TravisCurlProcessor(parameters, new OnlyRedirectHttpResponseCallback()).process(curlRequest)) {
				throw new BusinessException("Deleting the job for the subscription {} failed.", subscription);
			}
		}
	}

	/**
	 * Validate the administration connectivity.
	 *
	 * @param parameters
	 *            the administration parameters.
	 * @return job name.
	 */
	protected Job validateJob(final Map<String, String> parameters)
			throws MalformedURLException, URISyntaxException, IOException {
		// Get job's configuration
		final String job = parameters.get(PARAMETER_JOB);
		String jobJson = getResource(parameters, "/repos/" + encode(job));
		if (jobJson == null) {
			// Invalid couple PKEY and id
			throw new ValidationJsonException(PARAMETER_JOB, "travis-job", job);
		}

		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(jobJson);

		// Retrieve description, status and display name
		final Job result = new Job();

		JsonNode repo = node.get("repo");
		result.setName(repo.get("slug").asText());
		result.setDescription(repo.get("description").asText());
		final String statusNode = StringUtils.defaultString(repo.get("last_build_state").asText(), "red");
		result.setStatus(toStatus(statusNode));
		result.setLastBuildId(StringUtils.defaultString((repo.get("last_build_id").asText())));
		result.setBuilding("started".equals(statusNode));
		result.setId(job);
		return result;
	}

	private String encode(final String job) throws MalformedURLException, URISyntaxException {
		return new URI("http", job, "").toURL().getPath();
	}

	/**
	 * Return the node text without using document parser.
	 *
	 * @param xmlContent
	 *            XML content.
	 * @param node
	 *            the node name.
	 * @return trimmed node text or <code>null</code>.
	 */
	private String getNodeText(final String xmlContent, final String node) {
		final Matcher matcher = Pattern.compile("<" + node + ">([^<]*)</" + node + ">")
				.matcher(ObjectUtils.defaultIfNull(xmlContent, ""));
		if (matcher.find()) {
			return StringUtils.trimToNull(matcher.group(1));
		}
		return null;
	}

	/**
	 * Validate the basic REST connectivity to Jenkins.
	 *
	 * @param parameters
	 *            the server parameters.
	 * @return the detected Jenkins version.
	 */
	protected String validateAdminAccess(final Map<String, String> parameters) throws Exception {
		CurlProcessor.validateAndClose(StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "login",
				PARAMETER_URL, "jenkins-connection");

		// Check the user can log-in to Jenkins with the preempted
		// authentication processor
		if (getResource(parameters, "api/xml") == null) {
			throw new ValidationJsonException(PARAMETER_USER, "jenkins-login");
		}

		// Check the user has enough rights to get the master configuration and
		// return the version
		final String version = getVersion(parameters);
		if (version == null) {
			throw new ValidationJsonException(PARAMETER_USER, "jenkins-rights");
		}
		return version;
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
		return processor.get(StringUtils.appendIfMissing(url, "/") + resource);
	}

	@Override
	public String getVersion(final Map<String, String> parameters) throws Exception {
		// Check the user has enough rights to get the master configuration and
		// get the master configuration and
		return getResource(new TravisCurlProcessor(parameters, VERSION_CALLBACK), parameters.get(PARAMETER_URL),
				"api/json?tree=numExecutors");
	}

	/**
	 * Search the Jenkin's template jobs matching to the given criteria. Name,
	 * display name and description are considered.
	 *
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param criteria
	 *            the search criteria.
	 * @return template job names matching the criteria.
	 */
	@GET
	@Path("template/{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<Job> findAllTemplateByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws Exception {
		return findAllByName(node, criteria, "view/Templates/");
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
	public List<Job> findAllByName(@PathParam("node") final String node, @PathParam("criteria") final String criteria)
			throws Exception {
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
	public Job findById(@PathParam("node") final String node, @PathParam("id") final String id)
			throws MalformedURLException, URISyntaxException, IOException {
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
	private List<Job> findAllByName(final String node, final String criteria, final String view) throws Exception { // NOSONAR
																													// Too
																													// many
																													// exceptions

		final Map<String, String> parameters = pvResource.getNodeParameters(node);

		// Get the jobs and parse them
		final String url = StringUtils.trimToEmpty(view) + "repos?search=" + criteria + "&orderBy=name&limit=10";
		final InputStream jobsAsInput = IOUtils.toInputStream(getResource(parameters, url), StandardCharsets.UTF_8);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonNode = mapper.readTree(jobsAsInput);

		ArrayNode jobsNode = (ArrayNode) jsonNode.get("repos");

		return StreamSupport.stream(jobsNode.spliterator(), false).map(item -> {
			Job result = new Job();
			result.setName(item.get("slug").asText());
			result.setDescription(item.get("description").asText());
			final String statusNode = StringUtils.defaultString(item.get("last_build_state").asText(), "red");
			result.setStatus(toStatus(statusNode));
			result.setLastBuildId(StringUtils.defaultString((item.get("last_build_id").asText())));
			// result.setBuilding(statusNode.endsWith("_anime"));
			result.setId(item.get("slug").asText());
			return result;
		}).collect(Collectors.toList());
	}

	/**
	 * Return the color from the status of the job.
	 *
	 * @param status
	 *            last status for the job
	 * @return The color for the current status.
	 */
	private String toStatus(final String status) {
		return "passed".equals(status) ? "green" : "started".equals(status) ? "yellow" : "red";
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getLastVersion() throws Exception {
		// Get the download index from the default repository
		return getLastVersion(publicServer + "/war/");
	}

	/**
	 * Return the last version available for Jenkins for the given repository
	 * URL.
	 */
	protected String getLastVersion(final String repo) {
		// Get the download index
		final CurlProcessor processor = new CurlProcessor();
		final String downloadPage = ObjectUtils.defaultIfNull(processor.get(repo), "");

		// Find the last download link
		final Matcher matcher = Pattern.compile("href=\"([\\d.]+)/\"").matcher(downloadPage);
		String lastVersion = null;
		while (matcher.find()) {
			lastVersion = matcher.group(1);
		}

		// Return the last read version
		return lastVersion;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) throws Exception {
		// Status is UP <=> Administration access is UP
		validateAdminAccess(parameters);
		return true;
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
	public void build(@PathParam("subscription") final int subscription) throws Exception {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Check the instance is available
		Job job = validateJob(parameters);

		if (job.getLastBuildId() == null || !build(parameters, job)) {
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
			return processor.process(
					new CurlRequest("POST", travisBaseUrl + "/builds/" + job.getLastBuildId() + "/restart", null));
		} finally {
			processor.close();
		}
	}

}
