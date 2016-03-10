// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.auth.basic;

import com.microsoft.alm.auth.BaseAuthenticator;
import com.microsoft.alm.auth.PromptBehavior;
import com.microsoft.alm.auth.secret.Credential;
import com.microsoft.alm.helpers.Debug;
import com.microsoft.alm.storage.InsecureInMemoryStore;
import com.microsoft.alm.storage.SecretStore;

import java.net.URI;

/**
 * This authenticator returns username and password combos.
 */
public class BasicAuthAuthenticator extends BaseAuthenticator {

    private final static String TYPE = "BasicAuth";

    private final SecretStore<Credential> store;
    private final CredentialPrompt prompter;

    /**
     * Create BasicAuthAuthenticator with a in-memory secret store {@link InsecureInMemoryStore} and a Swing based
     * credential prompt.
     */
    public BasicAuthAuthenticator() {
        this(new InsecureInMemoryStore<Credential>(), new DefaultCredentialPrompt());
    }

    public BasicAuthAuthenticator(final SecretStore<Credential> store, final CredentialPrompt prompter) {
        Debug.Assert(store != null, "store cannot be null");
        Debug.Assert(prompter != null, "prompter cannot be null");

        this.store = store;
        this.prompter = prompter;
    }

    /**
     * Returns the type of this authenticator
     *
     * @return type BasicAuth
     */
    public String getAuthType() {
        return TYPE;
    }

    @Override
    protected SecretStore<Credential> getStore() {
        return this.store;
    }

    /**
     * This authetnicator supports return secret in the form of a {@link Credential} object
     *
     * @return {@code true}
     */
    @Override
    public boolean isCredentialSupported() {
        return true;
    }

    @Override
    public Credential getCredential(final URI uri) {
        return getCredential(uri, PromptBehavior.AUTO);
    }

    @Override
    public Credential getCredential(final URI uri,  final PromptBehavior promptBehavior) {
        Debug.Assert(uri != null, "getCrednetial uri key cannot be null");
        Debug.Assert(promptBehavior != null, "getCrednetial promptBehavior cannot be null");

        final String key = getKey(uri);

        SecretRetriever secretRetriever = new SecretRetriever() {
            @Override
            protected Credential doRetrieve() {
                return prompter.prompt(uri);
            }
        };

        return secretRetriever.retrieve(key, getStore(), promptBehavior);
    }

}
