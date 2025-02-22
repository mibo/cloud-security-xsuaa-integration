package com.sap.cloud.security.xsuaa.tokenflows;

import static com.sap.cloud.security.xsuaa.tokenflows.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.sap.cloud.security.xsuaa.client.ClientCredentials;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenResponse;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceEndpointsProvider;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import com.sap.cloud.security.xsuaa.client.OAuth2TokenService;
import com.sap.cloud.security.xsuaa.client.XsuaaDefaultEndpoints;

@RunWith(MockitoJUnitRunner.class)
public class ClientCredentialsTokenFlowTest {

	@Mock
	private OAuth2TokenService mockTokenService;

	private ClientCredentials clientCredentials;
	private ClientCredentialsTokenFlow cut;
	private OAuth2ServiceEndpointsProvider endpointsProvider;

	private static final String JWT_ACCESS_TOKEN = "4bfad399ca10490da95c2b5eb4451d53";

	@Before
	public void setup() {
		this.clientCredentials = new ClientCredentials("clientId", "clientSecret");
		this.endpointsProvider = new XsuaaDefaultEndpoints(XSUAA_BASE_URI);
		this.cut = new ClientCredentialsTokenFlow(mockTokenService, endpointsProvider, clientCredentials);
	}

	@Test
	public void constructor_throwsOnNullValues() {
		assertThatThrownBy(() -> {
			new ClientCredentialsTokenFlow(null, endpointsProvider, clientCredentials);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("OAuth2TokenService");

		assertThatThrownBy(() -> {
			new ClientCredentialsTokenFlow(mockTokenService, null, clientCredentials);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("OAuth2ServiceEndpointsProvider");

		assertThatThrownBy(() -> {
			new ClientCredentialsTokenFlow(mockTokenService, endpointsProvider, null);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("ClientCredentials");
	}

	@Test
	public void execute() throws TokenFlowException, OAuth2ServiceException {
		OAuth2TokenResponse accessToken = new OAuth2TokenResponse(JWT_ACCESS_TOKEN, 441231, null);

		Mockito.when(mockTokenService
				.retrieveAccessTokenViaClientCredentialsGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						isNull(), isNull()))
				.thenReturn(accessToken);

		OAuth2TokenResponse jwt = cut.execute();

		assertThat(jwt.getAccessToken(), is(accessToken.getAccessToken()));
	}

	@Test
	public void execute_throwsIfServiceRaisesException() throws OAuth2ServiceException {
		Mockito.when(mockTokenService
				.retrieveAccessTokenViaClientCredentialsGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						isNull(), isNull()))
				.thenThrow(new OAuth2ServiceException("exception executed REST call"));

		assertThatThrownBy(() -> {
			cut.execute();
		}).isInstanceOf(TokenFlowException.class)
				.hasMessageContaining(
						"Error requesting user token with grant_type 'client_credentials': exception executed REST call");
	}

	@Test
	public void execute_withAdditionalAuthorities() throws TokenFlowException, OAuth2ServiceException {
		OAuth2TokenResponse accessToken = new OAuth2TokenResponse(JWT_ACCESS_TOKEN, 441231, null);

		Map<String, String> additionalAuthorities = new HashMap<String, String>();
		additionalAuthorities.put("DummyAttribute", "DummyAttributeValue");

		Mockito.when(mockTokenService
				.retrieveAccessTokenViaClientCredentialsGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						isNull(), isNotNull()))
				.thenReturn(accessToken);

		OAuth2TokenResponse jwt = cut.attributes(additionalAuthorities)
				.execute();

		assertThat(jwt.getAccessToken(), is(accessToken.getAccessToken()));
	}

}
