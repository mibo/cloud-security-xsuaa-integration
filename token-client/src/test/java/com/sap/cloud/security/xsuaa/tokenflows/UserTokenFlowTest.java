package com.sap.cloud.security.xsuaa.tokenflows;

import static com.sap.cloud.security.xsuaa.tokenflows.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

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
import com.sap.cloud.security.xsuaa.test.JwtGenerator;

@RunWith(MockitoJUnitRunner.class)
public class UserTokenFlowTest {

	@Mock
	private OAuth2TokenService mockTokenService;

	@Mock
	RefreshTokenFlow mockRefreshTokenFlow;

	private String mockJwt;
	private String invalidMockJwt;
	private ClientCredentials clientCredentials;
	private UserTokenFlow cut;
	private OAuth2ServiceEndpointsProvider endpointsProvider;

	private static final String JWT_ACCESS_TOKEN = "4bfad399ca10490da95c2b5eb4451d53";
	private static final String REFRESH_TOKEN = "99e2cecfa54f4957a782f07168915b69-r";

	@Before
	public void setup() throws TokenFlowException {
		this.mockJwt = buildMockJwt();
		this.invalidMockJwt = buildInvalidMockJwt();
		this.clientCredentials = new ClientCredentials("clientId", "clientSecret");
		this.endpointsProvider = new XsuaaDefaultEndpoints(XSUAA_BASE_URI);
		this.cut = new UserTokenFlow(mockTokenService, mockRefreshTokenFlow, endpointsProvider, clientCredentials);

		// configure Refresh Token Flow Mock
		when(mockRefreshTokenFlow.execute()).thenReturn(new OAuth2TokenResponse(mockJwt, 4711, null));
		when(mockRefreshTokenFlow.refreshToken(anyString())).thenReturn(mockRefreshTokenFlow);
	}

	private String buildMockJwt() {
		return new JwtGenerator().addScopes("uaa.user").getToken().getTokenValue();
	}

	private String buildInvalidMockJwt() {
		return new JwtGenerator().getToken().getTokenValue();
	}

	@Test
	public void constructor_throwsOnNullValues() {
		assertThatThrownBy(() -> {
			new UserTokenFlow(null, mockRefreshTokenFlow, endpointsProvider, clientCredentials);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("OAuth2TokenService");

		assertThatThrownBy(() -> {
			new UserTokenFlow(mockTokenService, null,
					endpointsProvider, clientCredentials);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("RefreshTokenFlow");

		assertThatThrownBy(() -> {
			new UserTokenFlow(mockTokenService, mockRefreshTokenFlow, null, clientCredentials);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("OAuth2ServiceEndpointsProvider");

		assertThatThrownBy(() -> {
			new UserTokenFlow(mockTokenService, mockRefreshTokenFlow, endpointsProvider, null);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("ClientCredentials");
	}

	@Test
	public void execute_throwsIfMandatoryFieldsNotSet() {
		assertThatThrownBy(() -> {
			cut.execute();
		}).isInstanceOf(IllegalStateException.class);

		assertThatThrownBy(() -> {
			cut.execute();
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("User token not set");
	}

	@Test
	public void test_execute_throwsIfTokenDoesNotContainUaaUserScope() {
		assertThatThrownBy(() -> {
			new UserTokenFlow(mockTokenService, mockRefreshTokenFlow,
					endpointsProvider, clientCredentials)
							.token(invalidMockJwt)
							.execute();
		}).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT token does not include scope 'uaa.user'");
	}

	@Test
	public void execute_throwsIfServiceRaisesException() throws OAuth2ServiceException {
		when(mockTokenService
				.retrieveAccessTokenViaUserTokenGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						eq(mockJwt),
						isNull(), isNull()))
								.thenThrow(new OAuth2ServiceException("exception executed REST call"));

		assertThatThrownBy(() -> {
			cut.token(mockJwt)
					.execute();
		}).isInstanceOf(TokenFlowException.class)
				.hasMessageContaining(
						"Error requesting token with grant_type 'user_token'");
	}

	@Test
	public void execute() throws TokenFlowException, OAuth2ServiceException {
		OAuth2TokenResponse accessToken = new OAuth2TokenResponse(JWT_ACCESS_TOKEN, 441231, REFRESH_TOKEN);

		when(mockTokenService
				.retrieveAccessTokenViaUserTokenGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						eq(mockJwt),
						isNull(), isNull()))
								.thenReturn(accessToken);

		OAuth2TokenResponse jwt = cut.token(mockJwt)
				.execute();

		assertThat(jwt.getAccessToken(), is(mockJwt));

	}

	@Test
	public void execute_withSubdomain() throws TokenFlowException, OAuth2ServiceException {
		OAuth2TokenResponse accessToken = new OAuth2TokenResponse(JWT_ACCESS_TOKEN, 441231, REFRESH_TOKEN);
		String subdomain = "subdomain";
		RefreshTokenFlow refreshTokenFlow = new RefreshTokenFlow(mockTokenService, endpointsProvider,
				clientCredentials);
		cut = new UserTokenFlow(mockTokenService, refreshTokenFlow, endpointsProvider, clientCredentials);

		when(mockTokenService
				.retrieveAccessTokenViaUserTokenGrant(any(), any(), any(), any(), any()))
						.thenReturn(accessToken);

		cut.subdomain(subdomain).token(mockJwt).execute();

		Mockito.verify(mockTokenService, times(1))
				.retrieveAccessTokenViaUserTokenGrant(any(), any(), any(), eq(subdomain), any());
		Mockito.verify(mockTokenService, times(1))
				.retrieveAccessTokenViaRefreshToken(any(), any(), any(), eq(subdomain));
	}

	@Test
	public void execute_withAdditionalAuthorities() throws TokenFlowException, OAuth2ServiceException {
		OAuth2TokenResponse accessToken = new OAuth2TokenResponse(JWT_ACCESS_TOKEN, 441231, REFRESH_TOKEN);

		Map<String, String> additionalAuthorities = new HashMap<String, String>();
		additionalAuthorities.put("DummyAttribute", "DummyAttributeValue");

		Map<String, String> additionalAuthoritiesParam = new HashMap<>();
		additionalAuthoritiesParam.put("authorities", "{\"az_attr\":{\"DummyAttribute\":\"DummyAttributeValue\"}}");

		when(mockTokenService
				.retrieveAccessTokenViaUserTokenGrant(eq(TOKEN_ENDPOINT_URI), eq(clientCredentials),
						eq(mockJwt),
						isNull(), eq(additionalAuthoritiesParam)))
								.thenReturn(accessToken);

		OAuth2TokenResponse jwt = cut.token(mockJwt)
				.attributes(additionalAuthorities)
				.execute();

		assertThat(jwt.getAccessToken(), is(mockJwt));
	}

}