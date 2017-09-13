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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;

/**
 * Test class of {@link TravisPluginResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
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

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv", new Class[] { Node.class, Parameter.class, Project.class, Subscription.class,
				ParameterValue.class, DelegateOrg.class }, StandardCharsets.UTF_8.name());
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
	public void deleteRemote() throws Exception {
		addLoginAccess();
		addAdminAccess();

		// post delete
		final UrlPattern deletePath = urlEqualTo("/job/gfi-bootstrap/doDelete");
		httpServer.stubFor(post(deletePath).willReturn(
				aResponse().withHeader("location", "location").withStatus(HttpStatus.SC_MOVED_TEMPORARILY)));
		httpServer.start();

		resource.delete(subscription, true);

		// check that server has been called.
		httpServer.verify(1, WireMock.postRequestedFor(deletePath));
	}

	@Test(expected = BusinessException.class)
	public void deleteRemoteFailed() throws Exception {
		addLoginAccess();
		addAdminAccess();

		// post delete
		final UrlPattern deletePath = urlEqualTo("/job/gfi-bootstrap/doDelete");
		httpServer.stubFor(post(deletePath).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		resource.delete(subscription, true);
		// an error occured in this case.
	}

	@Test
	public void getJenkinsResourceInvalidUrl() {
		resource.getResource(new HashMap<>(), null);
	}

	@Test
	public void getVersion() throws Exception {
		addAdminAccess();
		httpServer.start();
		final String version = resource.getVersion(subscription);
		Assert.assertEquals("1.574", version);
	}

	@Test
	public void getLastVersion() throws Exception {
		final String lastVersion = resource.getLastVersion();
		Assert.assertNotNull(lastVersion);
		Assert.assertTrue(lastVersion.compareTo("1.576") > 0);
	}

	@Test
	public void getLastVersionFailed() throws Exception {
		Assert.assertNull(resource.getLastVersion("any:some"));
	}

	@Test
	public void validateJobNotFound() throws IOException, URISyntaxException {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(TravisPluginResource.PARAMETER_JOB, "travis-job"));
		httpServer.stubFor(
				get(urlEqualTo("/repos/gfi/bootstrap")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:build:travis:bpr");
		parameters.put(TravisPluginResource.PARAMETER_JOB, "gfi/bootstrap");
		resource.validateJob(parameters);
	}

	@Test
	public void link() throws Exception {
		addLoginAccess();
		addAdminAccess();
		addJobAccess();
		httpServer.start();

		// Attach the Jenkins project identifier
		final Parameter parameter = new Parameter();
		parameter.setId(TravisPluginResource.PARAMETER_JOB);
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setParameter(parameter);
		parameterValue.setData("gfi-bootstrap");
		parameterValue.setSubscription(subscription);
		em.persist(parameterValue);
		em.flush();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour jenkins
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void validateJob() throws IOException, URISyntaxException {
		addJobAccess();
		httpServer.start();

		final Map<String, String> parameters = pvResource.getNodeParameters("service:build:travis:bpr");
		parameters.put(TravisPluginResource.PARAMETER_JOB, "ligoj/plugin-vm-google");
		checkJob(resource.validateJob(parameters), false, "green");
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
		Assert.assertEquals("ligoj/plugin-vm-google", job.getId());
		Assert.assertEquals("ligoj/plugin-vm-google", job.getName());
		Assert.assertEquals("Ligoj plugin for Google instance life cycle management : scheduled ON/OFF",
				job.getDescription());
		Assert.assertEquals(status, job.getStatus());
		Assert.assertEquals(building, job.isBuilding());
	}

	@Test
	public void checkStatus() throws Exception {
		addLoginAccess();
		addAdminAccess();
		httpServer.start();

		final Map<String, String> parametersNoCheck = subscriptionResource.getParametersNoCheck(subscription);
		parametersNoCheck.remove(TravisPluginResource.PARAMETER_JOB);
		Assert.assertTrue(resource.checkStatus(parametersNoCheck));
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		addJobAccess();
		httpServer.start();

		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assert.assertTrue(nodeStatusWithData.getStatus().isUp());
		checkJob((Job) nodeStatusWithData.getData().get("job"), false, "green");
	}

	private void addJobAccess() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/plugin-vm-google")).willReturn(aResponse()
				.withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/travis/travis-ligoj-vm-google-config.json").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	private void addJobAccessBuilding() throws IOException {
		httpServer.stubFor(
				get(urlEqualTo("/repos/ligoj/plugin-vm-google")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/travis/travis-ligoj-vm-google-building.json")
										.getInputStream(),
								StandardCharsets.UTF_8))));
	}

	@Test
	public void validateAdminAccess() throws Exception {
		addLoginAccess();
		addAdminAccess();
		addJobAccess();
		httpServer.start();

		final String version = resource.validateAdminAccess(pvResource.getNodeParameters("service:build:jenkins:bpr"));
		Assert.assertEquals("1.574", version);
	}

	private void addAdminAccess() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/api/json?tree=numExecutors"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-jenkins", "1.574")
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/jenkins/jenkins-version.json").getInputStream(),
								StandardCharsets.UTF_8))));
	}

	@Test
	public void validateAdminAccessConnectivityFail() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(TravisPluginResource.PARAMETER_URL, "jenkins-connection"));
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();

		resource.validateAdminAccess(pvResource.getNodeParameters("service:build:jenkins:bpr"));
	}

	@Test
	public void validateAdminAccessLoginFail() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(TravisPluginResource.PARAMETER_USER, "jenkins-login"));
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/xml")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();

		resource.validateAdminAccess(pvResource.getNodeParameters("service:build:jenkins:bpr"));
	}

	@Test
	public void validateAdminAccessNoRight() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(TravisPluginResource.PARAMETER_USER, "jenkins-rights"));
		addLoginAccess();
		httpServer.stubFor(get(urlEqualTo("/computer/(master)/config.xml"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();

		resource.validateAdminAccess(pvResource.getNodeParameters("service:build:jenkins:bpr"));
	}

	@Test
	public void findJobsByName() throws Exception {
		httpServer
				.stubFor(get(urlPathEqualTo("/repos")).withQueryParam("search", equalTo("ligo"))
						.withQueryParam("orderBy", equalTo("name")).withQueryParam("limit", equalTo("10"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
								new ClassPathResource("mock-server/travis/travis-find-job.json")
										.getInputStream(),
								StandardCharsets.UTF_8))));

		httpServer.start();

		final List<Job> jobs = resource.findAllByName("service:build:travis:bpr", "ligo");
		Assert.assertEquals(5, jobs.size());
		Assert.assertEquals("ligoj/plugin-vm-aws", jobs.get(1).getName());
		Assert.assertEquals("Ligoj plugin for AWS EC2 instance life cycle management : scheduled ON/OFF",
				jobs.get(1).getDescription());
		Assert.assertEquals("ligoj/plugin-vm-aws", jobs.get(1).getId());
		Assert.assertEquals("green", jobs.get(1).getStatus());
	}

	@Test
	public void findJobsByIdSuccess() throws Exception {
		addJobAccessBuilding();
		httpServer.start();
		checkJob(resource.findById("service:build:travis:bpr", "ligoj/plugin-vm-google"), true, "yellow");
	}

	@Test(expected = ValidationJsonException.class)
	public void findJobsByIdFail() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/repos/ligoj/plugin-vm-googlee"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND).withBody("{\"file\":\"not found\"}")));
		httpServer.start();
		resource.findById("service:build:travis:bpr", "ligoj/plugin-vm-googlee");
	}

	private void addLoginAccess() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/login")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/xml")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/jenkins/jenkins-api-xml.xml").getInputStream(),
						StandardCharsets.UTF_8))));
	}

	@Test(expected = BusinessException.class)
	public void buildFailed() throws Exception {
		addLoginAccess();
		addAdminAccess();
		httpServer.start();
		this.resource.build(subscription);
	}

	@Test
	public void build() throws Exception {
		addJobAccess();
		httpServer.stubFor(
				post(urlEqualTo("/builds/274572860/restart")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();
		this.resource.build(subscription);
	}

	@Test(expected = RuntimeException.class)
	public void buildInvalidUrl() throws Exception {
		Map<String, String> map = Mockito.mock(Map.class);
		Mockito.when(map.get(TravisPluginResource.PARAMETER_USER)).thenReturn("some");
		Mockito.when(map.get(TravisPluginResource.PARAMETER_TOKEN)).thenReturn("some");
		Mockito.when(map.get(TravisPluginResource.PARAMETER_URL)).thenThrow(new RuntimeException());
		this.resource.build(map, null);
	}

}
