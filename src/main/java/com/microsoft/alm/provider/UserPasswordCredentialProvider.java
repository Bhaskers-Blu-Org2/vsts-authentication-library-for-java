// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.provider;

import com.microsoft.alm.auth.Authenticator;
import com.microsoft.alm.auth.PromptBehavior;
import com.microsoft.alm.auth.secret.Credential;
import com.microsoft.alm.auth.secret.Token;
import com.microsoft.alm.auth.secret.TokenPair;
import com.microsoft.alm.helpers.Debug;

import java.net.URI;

/**
 * Provides authentication data in the form of username / password combos
 *
 * For OAuth2 and Personal Access Token, there are really no username.  In those cases, return a hardcoded username
 * that identifies the type of authentication instead of a name that identifies the user.
 */
public class UserPasswordCredentialProvider {

    private Authenticator authenticator;

    public UserPasswordCredentialProvider(final Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Get a global credential that works across all accounts.
     *
     * Noted there is no Basic Auth data that works across all accounts, hence a Basic Auth anthenticator backed
     * instance would return null.
     *
     * This method will prompt the user if there is no global credential available.  In case of PAT, it will generate
     * one according to default {@link Options}
     *
     * @return credential object that works across all accounts (but maybe scoped in case of PATs).
     *         {@code null} when authentication failed.
     */
    public Credential getCredential() {
        return getCredential(PromptBehavior.AUTO, Options.getDefaultOptions());
    }

    /**
     * Get a global credential that works across all accounts.
     *
     * Noted there is no Basic Auth data that works across all accounts, hence a Basic Auth anthenticator backed
     * instance would return null.
     *
     * This method will prompt the user if there is no global credential available and the specified
     * {@link PromptBehavior} allows it.  In case of PAT, it  will generate one according to the specified
     * {@link Options} too.
     *
     * @return credential object that works across all accounts (but maybe scoped in case of PATs).
     *         {@code null} when authentication failed.
     */
    public Credential getCredential(final PromptBehavior promptBehavior, final Options options) {
        Debug.Assert(promptBehavior != null, "promptBehavior cannot be null");
        Debug.Assert(options != null, "options cannot be null");

        final String username = authenticator.getAuthType();

        String password = null;
        if (authenticator.isOAuth2TokenSupported()) {
            final TokenPair tokenPair = authenticator.getOAuth2TokenPair(promptBehavior);

            if (tokenPair != null && tokenPair.AccessToken != null) {
                password = tokenPair.AccessToken.Value;
            }

        } else if (authenticator.isPersonalAccessTokenSupported()) {
            final Token token = authenticator.getPersonalAccessToken(
                    options.patGenerationOptions.tokenScope,
                    options.patGenerationOptions.displayName,
                    promptBehavior);

            if (token != null) {
                password = token.Value;
            }
        }

        return createCreds(username, password);
    }

    /**
     * Get a credential that works for the specified account uri.
     *
     * This method will prompt the user if there is no credential available for the account.  In case of PAT, it will
     * generate one according to default {@link Options}
     *
     * @param uri
     *      account uri
     *
     * @return credential object for the specified account (but maybe scoped in case of PATs)
     *         {@code null} when authentication failed.
     */
    public Credential getCredentialFor(final URI uri) {
        return getCredentialFor(uri, PromptBehavior.AUTO, Options.getDefaultOptions());
    }

    /**
     * Get a credential that works for the specified account uri.
     *
     * This method will prompt the user if there is no credential available for the account and the specified
     * {@link PromptBehavior} allows prompting.  In case of PAT, it  will generate one according to the specified
     * {@link Options} too.
     *
     * @param uri
     *      target uri we want to send request against
     * @param promptBehavior
     *      dictates we allow prompting the user or not.  In case of VstsPatAuthenticator, prompting also means generate
     *      a new PAT.
     * @param options
     *      options specified by users.
     *
     * @return credential object that works across all accounts (but maybe scoped in case of PATs).
     *         {@code null} when authentication failed.
     */
    public Credential getCredentialFor(final URI uri, final PromptBehavior promptBehavior,
                                       final Options options) {
        Debug.Assert(uri != null, "uri cannot be null");
        Debug.Assert(promptBehavior != null, "promptBehavior cannot be null");
        Debug.Assert(options != null, "options cannot be null");

        String username = null;
        String password = null;

        if (authenticator.isCredentialSupported()) {
            final Credential credential = authenticator.getCredential(uri, promptBehavior);
            if (credential != null) {
                username = credential.Username;
                password = credential.Password;
            }

        } else if (authenticator.isOAuth2TokenSupported()) {
            final TokenPair tokenPair = authenticator.getOAuth2TokenPair(promptBehavior);

            if (tokenPair != null && tokenPair.AccessToken != null) {
                username = authenticator.getAuthType();
                password = tokenPair.AccessToken.Value;
            }

        } else if (authenticator.isPersonalAccessTokenSupported()) {
            final Token token = authenticator.getPersonalAccessToken(uri,
                    options.patGenerationOptions.tokenScope,
                    options.patGenerationOptions.displayName,
                    promptBehavior);

            if (token != null) {
                username = authenticator.getAuthType();
                password = token.Value;
            }
        }

        return createCreds(username, password);
    }

    private Credential createCreds(final String username, final String password) {
        return (username != null && password != null) ? new Credential(username, password) : null;
    }
}
