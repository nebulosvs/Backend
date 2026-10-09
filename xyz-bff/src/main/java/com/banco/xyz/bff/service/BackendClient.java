package com.banco.xyz.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BackendClient {

	private final OAuth2AuthorizedClientManager authorizedClientManager;
	private final RestClient restClient;

	public BackendClient(
			OAuth2AuthorizedClientManager authorizedClientManager,
			@Value("${bank.backend.base-url}") String baseUrl) {

		this.authorizedClientManager = authorizedClientManager;

		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.build();
	}

	public RestClient.RequestHeadersSpec<?> get(
			String registrationId,
			String uri) {

		return restClient.get()
				.uri(uri)
				.headers(headers ->
						headers.setBearerAuth(
								token(registrationId)));
	}

	public RestClient.RequestBodySpec post(
			String registrationId,
			String uri) {

		return restClient.post()
				.uri(uri)
				.headers(headers ->
						headers.setBearerAuth(
								token(registrationId)));
	}

	private String token(String registrationId) {

		OAuth2AuthorizeRequest request =
				OAuth2AuthorizeRequest
						.withClientRegistrationId(registrationId)
						.principal(registrationId + "-bff")
						.build();

		OAuth2AuthorizedClient client =
				authorizedClientManager.authorize(request);

		if (client == null || client.getAccessToken() == null) {
			throw new IllegalStateException(
					"No fue posible obtener token OAuth2 para "
							+ registrationId);
		}

		return client.getAccessToken()
				.getTokenValue();
	}
}