// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.provider;

import com.microsoft.alm.auth.Authenticator;
import com.microsoft.alm.auth.PromptBehavior;
import com.microsoft.alm.auth.secret.Credential;
import com.microsoft.alm.auth.secret.Token;
import com.microsoft.alm.auth.secret.TokenPair;
import com.microsoft.alm.helpers.Debug;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.net.URI;

/**
 * TODO: we need to add proxy setting and such
 */
public class JaxrsClientProvider {

    private Authenticator authenticator;

    /**
     * Provides authenticated JAX RS clients based on {@link Authenticator} provided
     *
     * @param authenticator
     *      an authenticator that handles generates authentication data
     */
    public JaxrsClientProvider(final Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Get a globally authenticated JAXRS client - a client that can potentially access all accounts the user owns with
     * ${@link PromptBehavior} AUTO.
     *
     * Be aware that a globally authenticated client DOES NOT mean the client can represent the principal and has
     * permission to everything the user owns.  If backed by {@link com.microsoft.alm.auth.pat.VstsPatAuthenticator}
     * the client is limited to the scopes defined in the Personal Access Token.  If no Personal Access {@link Token}
     * exists, it will generate one with the default {@link Options}.
     *
     * @return client
     *      authenticated JAXRS client.  {@code null} if authentication failed.
     */
    public Client getClient() {
        return getClient(PromptBehavior.AUTO, Options.getDefaultOptions());
    }

    /**
     * Get a globally authenticated JAXRS client - a client that can potentially access all accounts the user owns with
     * the specified {@link PromptBehavior} behavior.
     *
     * Be aware that a globally authenticated client DOES NOT mean the client can represent the principal and has
     * permission to everything the user owns.  If backed by {@link com.microsoft.alm.auth.pat.VstsPatAuthenticator}
     * the client is limited to the scopes defined in the Personal Access Token. If no Personal Access {@link Token}
     * exists, it will generate one with the specified {@link Options} if we allow PAT generation with the specified
     * prompt behavior.
     *
     * @param promptBehavior
     *      dictates we allow prompting the user or not.  In case of VstsPatAuthenticator, prompting also means generate
     *      a new PAT.
     * @param options
     *      options specified by users.
     *
     * @return client
     *      authenticated JAXRS client.  {@code null} if authentication failed.
     */
    public Client getClient(final PromptBehavior promptBehavior, final Options options) {
        Debug.Assert(promptBehavior != null, "promptBehavior cannot be null");
        Debug.Assert(options != null, "options cannot be null");

        Client client = null;

        if (authenticator.isOAuth2TokenSupported()) {
            final TokenPair tokenPair = authenticator.getOAuth2TokenPair(promptBehavior);
            client = getClientWithOAuth2RequestFilter(tokenPair);

        }
        // Get a client backed by a global PAT
        else if (authenticator.isPersonalAccessTokenSupported()) {
            final Token token = authenticator.getPersonalAccessToken(
                    options.patGenerationOptions.tokenScope,
                    options.patGenerationOptions.displayName,
                    promptBehavior);

            if (token != null) {
                client = getClientWithUsernamePassword(authenticator.getAuthType(), token.Value);
            }
        }

        return client;
    }

    /**
     * Get an authenticated JAXRS client for the specific account URI with {@link PromptBehavior} AUTO.
     *
     * If backed by {@link com.microsoft.alm.auth.pat.VstsPatAuthenticator} and no PAT exists,  will generate one
     * with default {@link Options}.
     *
     * @param uri
     *      target uri we want to send request against
     *
     * @return client
     *      authenticated JAXRS client.  {@code null} if authentication failed.
     */
    public Client getClientFor(final URI uri) {
        return getClientFor(uri, PromptBehavior.AUTO, Options.getDefaultOptions());
    }

    /**
     * Get an authenticated JAXRS client for the specified account with the specified {@link PromptBehavior} behavior.
     *
     * If backed by {@link com.microsoft.alm.auth.pat.VstsPatAuthenticator} and no PAT exists, will generate one with
     * the specified {@link Options} if we allow PAT generation with the specified prompt behavior.
     *
     * @param uri
     *      target uri we want to send request against
     * @param promptBehavior
     *      dictates we allow prompting the user or not.  In case of VstsPatAuthenticator, prompting also means generate
     *      a new PAT.
     * @param options
     *      options specified by users.
     *
     * @return client
     *      authenticated JAXRS client.  {@code null} if authentication failed.
     */
    public Client getClientFor(final URI uri, final PromptBehavior promptBehavior, final Options options) {
        Debug.Assert(uri != null, "uri cannot be null");
        Debug.Assert(promptBehavior != null, "promptBehavior cannot be null");
        Debug.Assert(options != null, "options cannot be null");

        Client client = null;

        if (authenticator.isCredentialSupported()) {
            final Credential credential = authenticator.getCredential(uri, promptBehavior);
            if (credential != null) {
                client = getClientWithUsernamePassword(credential.Username, credential.Password);
            }
        }
        /*
         * Although this function calls for a URI specific client, the client returned by the OAuth2 provider is still
         * global as OAuth2 token is not scoped to one account
         */
        else if (authenticator.isOAuth2TokenSupported()) {
            final TokenPair tokenPair = authenticator.getOAuth2TokenPair(promptBehavior);
            client = getClientWithOAuth2RequestFilter(tokenPair);
        }

        else if (authenticator.isPersonalAccessTokenSupported()) {
            final Token token = authenticator.getPersonalAccessToken(
                    uri,
                    options.patGenerationOptions.tokenScope,
                    options.patGenerationOptions.displayName,
                    promptBehavior);

            if (token != null) {
                client = getClientWithUsernamePassword(authenticator.getAuthType(), token.Value);
            }
        }

        return client;
    }

    private Client getClientWithOAuth2RequestFilter(final TokenPair tokenPair) {
        // default Jersey client with HttpURLConnection as the connector
        final Client client;

        if (tokenPair != null && tokenPair.AccessToken != null) {
            client = ClientBuilder.newClient();
            client.register(new ClientRequestFilter() {
                @Override
                public void filter(final ClientRequestContext requestContext) throws IOException {
                    requestContext.getHeaders().putSingle("Authorization", "Bearer " + tokenPair.AccessToken.Value);
                }
            });
        } else {
            client = null;
        }

        return client;
    }

    private Client getClientWithUsernamePassword(final String username, final String password) {
        final ClientConfig clientConfig = getClientConfig(username, password);

        return ClientBuilder.newClient(clientConfig);
    }

    private ClientConfig getClientConfig(final String username, final String password) {
        Debug.Assert(username != null, "username cannot be null");
        Debug.Assert(password != null, "password cannot be null");

        final Credentials credentials
                = new UsernamePasswordCredentials(username, password);

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        final ConnectorProvider connectorProvider = new ApacheConnectorProvider();

        final ClientConfig clientConfig = new ClientConfig().connectorProvider(connectorProvider);
        clientConfig.property(ApacheClientProperties.CREDENTIALS_PROVIDER, credentialsProvider);

        clientConfig.property(ApacheClientProperties.PREEMPTIVE_BASIC_AUTHENTICATION, true);
        clientConfig.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);

        return clientConfig;
    }

}
