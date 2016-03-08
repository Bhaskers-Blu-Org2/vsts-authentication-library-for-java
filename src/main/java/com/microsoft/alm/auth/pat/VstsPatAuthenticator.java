// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.auth.pat;

import com.microsoft.alm.auth.BaseAuthenticator;
import com.microsoft.alm.auth.PromptBehavior;
import com.microsoft.alm.auth.oauth.OAuth2Authenticator;
import com.microsoft.alm.auth.secret.Token;
import com.microsoft.alm.auth.secret.TokenPair;
import com.microsoft.alm.auth.secret.VsoTokenScope;
import com.microsoft.alm.helpers.Debug;
import com.microsoft.alm.helpers.StringHelper;
import com.microsoft.alm.storage.SecretStore;

import java.net.URI;

public class VstsPatAuthenticator extends BaseAuthenticator {

    private final static String TYPE = "PersonalAccessToken";

    private final VsoAzureAuthority vsoAzureAuthority;

    private final OAuth2Authenticator vstsOauthAuthenticator;

    private final SecretStore<Token> store;

    /**
     * Create a Personal Access Token Authenticator backed by the OAuth2 app with {@code oauthClientId} and
     * {@code oauthClientRedirectUri}.
     *
     * The oauthTokenStore will be utilized to check if there is valid OAuth2 {@link TokenPair} available first
     *
     * @param oauthClientId
     *      registered OAuth2 client id
     * @param oauthClientRedirectUrl
     *      registered OAuth2 client redirect URI
     * @param oauthTokenStore
     *      A secret store that will be used to check for available OAuth2 TokenPair
     * @param store
     *      Store for personal access tokens
     */
    public VstsPatAuthenticator(final String oauthClientId, final String oauthClientRedirectUrl,
                                final SecretStore<TokenPair> oauthTokenStore,
                                final SecretStore<Token> store) {
        Debug.Assert(oauthClientId!= null, "oauthClientId cannot be null");
        Debug.Assert(oauthClientRedirectUrl!= null, "oauthClientRedirectUrl cannot be null");
        Debug.Assert(store != null, "store cannot be null");

        this.vstsOauthAuthenticator = OAuth2Authenticator.getAuthenticator(oauthClientId,
                oauthClientRedirectUrl, oauthTokenStore);
        this.vsoAzureAuthority = new VsoAzureAuthority();
        this.store = store;
    }

    /* default */ VstsPatAuthenticator(final VsoAzureAuthority vsoAzureAuthority,
                                       final OAuth2Authenticator oauth2Authenticator,
                                        final SecretStore<Token> store) {
        this.vsoAzureAuthority = vsoAzureAuthority;
        this.vstsOauthAuthenticator = oauth2Authenticator;
        this.store = store;
    }

    @Override
    protected SecretStore<Token> getStore() {
        return this.store;
    }

    @Override
    public String getAuthType() {
        return TYPE;
    }

    @Override
    public boolean isPersonalAccessTokenSupported() {
        return true;
    }

    @Override
    public Token getPersonalAccessToken(final VsoTokenScope tokenScope, final String patDisplayName,
                                        final PromptBehavior promptBehavior) {
        // Global PAT will be stored with URI key APP_VSSPS_VISUALSTUDIO as this key doesn't identify any account
        return getToken(vstsOauthAuthenticator.APP_VSSPS_VISUALSTUDIO, true, tokenScope, patDisplayName, promptBehavior);
    }

    @Override
    public Token getPersonalAccessToken(final URI uri, final VsoTokenScope tokenScope, final String patDisplayName,
                                        final PromptBehavior promptBehavior) {
        return getToken(uri, false, tokenScope, patDisplayName, promptBehavior);
    }

    private Token getToken(final URI uri, final boolean isCreatingGlobalPat,
                           final VsoTokenScope tokenScope, final String patDisplayName,
                           final PromptBehavior promptBehavior) {
        Debug.Assert(uri != null, "uri cannot be null");
        Debug.Assert(promptBehavior != null, "promptBehavior cannot be null");

        if (!isHosted(uri)) {
            throw new RuntimeException("Only works against VisualStudio Team Services");
        }

        final String key = getKey(uri);

        Debug.Assert(key != null, "Failed to convert uri to key");

        SecretRetriever secretRetriever = new SecretRetriever() {
            @Override
            protected Token doRetrieve() {
                TokenPair oauthToken = vstsOauthAuthenticator.getOAuth2TokenPair(promptBehavior.AUTO);

                if (oauthToken == null) {
                    // authentication failed, return null
                    return null;
                }

                if (oauthToken != null) {
                    final Token token = vsoAzureAuthority.generatePersonalAccessToken(uri, oauthToken.AccessToken,
                            tokenScope, true, isCreatingGlobalPat, patDisplayName);

                    return token;
                }

                return null;
            }
        };

        return secretRetriever.retrieve(key, getStore(), promptBehavior);
    }

    /**
     * "Forget" the global PAT, also remove the oauth token to force sign in again
     *
     * @return {@code true} if global PAT and the OAuth2 token used to generate this PAT are both forgotten
     */
    @Override
    public boolean signOut() {
        return this.signOut(vstsOauthAuthenticator.APP_VSSPS_VISUALSTUDIO);
    }

    @Override
    public boolean signOut(final URI uri) {
        Debug.Assert(uri != null, "uri cannot be null");

        return super.signOut(uri)
                && vstsOauthAuthenticator.signOut();
    }

    /**
     * @deprecated Global PAT is going away soon
     *
     * Since global PAT suppose to work across accounts, we can associate the global PAT to a particular account
     * and everything should still work.
     *
     * @param uri
     *      Target account uri
     *
     * @return {@code true} if there is a global PAT and we successfully associated it with the target uri
     *         {@code false} otherwise
     */
    public boolean assignGlobalPatTo(final URI uri) {
        Debug.Assert(uri != null, "uri cannot be null");

        final String globalKey = getKey(vstsOauthAuthenticator.APP_VSSPS_VISUALSTUDIO);
        final Token token = getStore().get(globalKey);
        if (token != null) {
            assign(uri, token);
            return true;
        }

        return false;
    }

    private boolean isHosted(final URI targetUri) {
        final String VsoBaseUrlHost = "visualstudio.com";
        return StringHelper.endsWithIgnoreCase(targetUri.getHost(), VsoBaseUrlHost);
    }

    private void assign(final URI uri, final Token token) {
        final String key = getKey(uri);
        getStore().add(key, token);
    }
}
