package org.ligoj.app.plugin.build.travis;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.build.BuildResource;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link TravisPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class TravisPluginResourceTest extends AbstractServerTest {
	@Autowired
	private TravisPluginResource resource;

	@Autowired
	private ParameterValueResource pvResource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	protected int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class, DelegateOrg.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	protected int getSubscription(final String project) {
		return getSubscription(project, BuildResource.SERVICE_KEY);
	}

	@Test
	public void deleteLocal() throws Exception {
		resource.delete(subscription, false);
		// nothing has been done. If remote delete is done, an exception will be
		// thrown and this test will fail.
	}

	@Test
	public void getJenkinsResourceInvalidUrl() {
		resource.getResource(new HashMap<>(), null);
	}

	@Test
	public void validateJobNotFound() {
		httpServer.stubFor(get(urlEqualTo("/repos/gfi/bootstrap")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:build:travis:bpr");
		parameters.put(TravisPluginResource.PARAMETER_JOB, "gfi/bootstrap");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateJob(parameters);
		}), TravisPluginResource.PARAMETER_JOB, "travis-job");
	}

	@Test
	public void link() throws Exception {
		// addLoginAccess();
		// addAdminAccess();
		addJobAccess();
		httpServer.start();

		int gStack2 = getSubscription("gStack2");

		// Attach the Jenkins project identifier
		final Parameter parameter = new Parameter();
		parameter.setId(TravisPluginResource.PARAMETER_JOB);
		final Subscription subscriptionGstack2 = em.find(Subscription.class, gStack2);

		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setParameter(parameter);
		parameterValue.setData("ligoj/plugin-vm-google");
		parameterValue.setSubscription(subscriptionGstack2);
		em.persist(parameterValue);
		em.flush();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour jenkins
		resource.link(gStack2);

		// Nothing to validate for now...
	}

	@Test
	public void validateJob() throws IOException, URISyntaxException {
		addJobAccess();
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:build:travis:bpr");
		parameters.put(TravisPluginResource.PARAMETER_JOB, "ligoj/plugin-vm-google");
		checkJob(resource.validateJob(parameters), false, "blue");
	}

	@Test
	public void validateJobBuilding() throws IOException, URISyntaxException {
		addJobAccessBuilding();
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:build:travis:bpr");
		parameters.put(TravisPluginResource.PARAMETER_JOB, "ligoj/plugin-vm-google");
		checkJob(resource.validateJob(parameters), true, "yellow");
	}

	private void checkJob(final Job job, final boolean building, final String status) {
		Assertions.assertEquals("ligoj/plugin-vm-google", job.getId());
		Assertions.assertEquals("ligoj/plugin-vm-google", job.getName());
		Assertions.assertEquals("Ligoj plugin for Google instance life cycle management : scheduled ON/OFF", job.getDescription());
		Assertions.assertEquals(status, job.getStatus());
		Assertions.assertEquals(building, job.isBuilding());
	}

	@Test
	public void checkStatus() throws Exception {
		addConfigAccess();
		httpServer.start();

		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove(TravisPluginResource.PARAMETER_JOB);
		Assertions.assertTrue(resource.checkStatus(parametersNoCheck));
	}

	@Test
	public void checkStatusFailed() throws Exception {
		httpServer.start();

		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove(TravisPluginResource.PARAMETER_JOB);
		Assertions.assertFalse(resource.checkStatus(parametersNoCheck));
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		addJobAccess();
		httpServer.start();

		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
		checkJob((Job) nodeStatusWithData.getData().get("job"), false, "blue");
	}

	private void addJobAccess() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/plugin-vm-google")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/travis/travis-ligoj-vm-google-config.json").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	private void addJobAccessBuilding() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/plugin-vm-google")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
				IOUtils.toString(new ClassPathResource("mock-server/travis/travis-ligoj-vm-google-building.json").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	private void addConfigAccess() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/config")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils
				.toString(new ClassPathResource("mock-server/travis/travis-config.json").getInputStream(), StandardCharsets.UTF_8))));
	}

	@Test
	public void findJobsByName() throws Exception {
		httpServer
				.stubFor(get(urlPathEqualTo("/repos")).withQueryParam("search", equalTo("ligo")).withQueryParam("orderBy", equalTo("name"))
						.withQueryParam("limit", equalTo("10"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
								IOUtils.toString(new ClassPathResource("mock-server/travis/travis-find-job.json").getInputStream(),
										StandardCharsets.UTF_8))));

		httpServer.start();

		final List<Job> jobs = resource.findAllByName("service:build:travis:bpr", "ligo");
		Assertions.assertEquals(5, jobs.size());
		Assertions.assertEquals("ligoj/plugin-vm-aws", jobs.get(1).getName());
		Assertions.assertEquals("Ligoj plugin for AWS EC2 instance life cycle management : scheduled ON/OFF", jobs.get(1).getDescription());
		Assertions.assertEquals("ligoj/plugin-vm-aws", jobs.get(1).getId());
		Assertions.assertEquals("blue", jobs.get(1).getStatus());
	}

	@Test
	public void findJobsByNameAuthFailed() throws Exception {
		// All queries would fail
		httpServer.start();

		Assertions.assertEquals(0, resource.findAllByName("service:build:travis:bpr", "ligo").size());
	}

	@Test
	public void findJobsByIdSuccess() throws Exception {
		addJobAccessBuilding();
		httpServer.start();
		checkJob(resource.findById("service:build:travis:bpr", "ligoj/plugin-vm-google"), true, "yellow");
	}

	@Test
	public void findJobsByIdFail() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/any"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND).withBody("{\"file\":\"not found\"}")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.findById("service:build:travis:bpr", "ligoj/any");
		}), TravisPluginResource.PARAMETER_JOB, "travis-job");
	}

	@Test
	public void buildNotExists() throws Exception {
		httpServer.start();
		Assertions.assertEquals(Assertions.assertThrows(BusinessException.class, () -> {
			this.resource.build(subscription);
		}).getMessage(), "Launching the job for the subscription {} failed.");
	}

	@Test
	public void buildFailed() throws Exception {
		addJobAccess();
		httpServer.stubFor(post(urlEqualTo("/builds/274572860/restart")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
		httpServer.start();
		Assertions.assertEquals(Assertions.assertThrows(BusinessException.class, () -> {
			this.resource.build(subscription);
		}).getMessage(), "Launching the job for the subscription {} failed.");
	}

	@Test
	public void buildFailedBecauseNoBuildAvailabled() throws Exception {

		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/plugin-vm-google")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/travis/travis-ligoj-vm-google-building-failed.json").getInputStream(),
						StandardCharsets.UTF_8))));

		httpServer.start();
		Assertions.assertEquals(Assertions.assertThrows(BusinessException.class, () -> {
			this.resource.build(subscription);
		}).getMessage(), "Launching the job for the subscription {} failed.");
	}

	@Test
	public void build() throws Exception {
		addJobAccess();
		httpServer.stubFor(post(urlEqualTo("/builds/274572860/restart")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		this.resource.build(subscription);
	}

	@Test
	public void buildInvalidUrl() throws Exception {
		@SuppressWarnings("unchecked")
		final Map<String, String> map = Mockito.mock(Map.class);
		Mockito.when(map.get(TravisPluginResource.PARAMETER_USER)).thenReturn("some");
		Mockito.when(map.get(TravisPluginResource.PARAMETER_TOKEN)).thenReturn("some");
		Mockito.when(map.get(TravisPluginResource.PARAMETER_URL)).thenThrow(new RuntimeException("some"));
		Assertions.assertEquals(Assertions.assertThrows(RuntimeException.class, () -> {
			this.resource.build(map, null);
		}).getMessage(), "some");
	}

}
