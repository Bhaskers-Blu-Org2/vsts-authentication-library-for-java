// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.storage.windows.internal;

import com.microsoft.alm.auth.secret.Secret;
import com.microsoft.alm.helpers.StringHelper;
import com.microsoft.alm.helpers.SystemHelper;
import com.microsoft.alm.storage.SecretStore;
import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

import java.util.Arrays;


/**
 * This class exposes functions to interact with Windows Credential Manager
 */
public abstract class CredManagerBackedSecureStore<E extends Secret> implements SecretStore<E> {

    private final CredAdvapi32 INSTANCE = getCredAdvapi32Instance();

    /**
     * Create a secret from the string reprsentation
     */
    protected abstract E create(String username, String secret);

    /**
     * Get String representation of the UserName field from the secret
     */
    protected abstract String getUsername(E secret);

    /**
     * Get String representation of the CredentialBlob field from the secret
     */
    protected abstract String getCredentialBlob(E secret);

    /**
     * Read calls CredRead on Windows and retrieve the Secret
     *
     * Multi-thread safe, synchronized access to store
     *
     * @param key
     *      TargetName in the credential structure
     */
    @Override
    public E get(String key) {
        final CredAdvapi32.PCREDENTIAL pcredential = new CredAdvapi32.PCREDENTIAL();
        boolean read = false;
        E cred;

        try {
            // MSDN doc doesn't mention threading safety, so let's just be careful and synchronize the access
            synchronized (INSTANCE) {
                read = INSTANCE.CredRead(key, CredAdvapi32.CRED_TYPE_GENERIC, 0, pcredential);
            }

            if (read) {
                final CredAdvapi32.CREDENTIAL credential = new CredAdvapi32.CREDENTIAL(pcredential.credential);

                byte[] secretBytes = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
                final String secret = StringHelper.UTF8GetString(secretBytes);
                final String username = credential.UserName;

                cred = create(username, secret);

            } else {
                cred = null;
            }

        } catch (final LastErrorException e) {
            //TODO: Add logger
            cred = null;

        } finally {
            if (pcredential.credential != null) {
                synchronized (INSTANCE) {
                    INSTANCE.CredFree(pcredential.credential);
                }
            }
        }

        return cred;
    }

    /**
     * Delete the stored credential from Credential Manager
     *
     * Multi-thread safe, synchronized access to store
     *
     * @param key
     *      TargetName in the credential structure
     *
     * @return
     *      true if delete successful, false otherwise (including key doesn't exist)
     */
    @Override
    public boolean delete(String key) {
        try {
            synchronized (INSTANCE) {
                boolean deleted = INSTANCE.CredDelete(key, CredAdvapi32.CRED_TYPE_GENERIC, 0);

                return deleted;
            }
        } catch (LastErrorException e) {
            //TODO: Add logger
            return false;
        }
    }

    /**
     * Add the specified secret to Windows Credential Manager
     *
     * Multi-thread safe, synchronized access to store
     *
     * @param key
     *      TargetName in the credential structure
     * @param secret
     *      Secret that will be saved
     */
    @Override
    public void add(String key, E secret) {
        final String username = getUsername(secret);
        final String credentialBlob = getCredentialBlob(secret);
        byte[] credBlob = StringHelper.UTF8GetBytes(credentialBlob);

        final CredAdvapi32.CREDENTIAL cred = buildCred(key, username, credBlob);

        try {
            synchronized (INSTANCE) {
                INSTANCE.CredWrite(cred, 0);
            }
        } finally {
            cred.CredentialBlob.clear(credBlob.length);
            Arrays.fill(credBlob, (byte) 0);
        }
    }

    private CredAdvapi32.CREDENTIAL buildCred(String key, String username, byte[] credentialBlob) {
        final CredAdvapi32.CREDENTIAL credential = new CredAdvapi32.CREDENTIAL();

        credential.Flags = 0;
        credential.Type = CredAdvapi32.CRED_TYPE_GENERIC;
        credential.TargetName = key;


        credential.CredentialBlobSize = credentialBlob.length;
        credential.CredentialBlob = getPointer(credentialBlob);

        credential.Persist = CredAdvapi32.CRED_PERSIST_LOCAL_MACHINE;
        credential.UserName = username;

        return credential;
    }

    private Pointer getPointer(byte[] array) {
        Pointer p = new Memory(array.length);
        p.write(0, array, 0, array.length);

        return p;
    }

    private static CredAdvapi32 getCredAdvapi32Instance() {
        if (SystemHelper.isWindows()) {
            return CredAdvapi32.INSTANCE;
        } else {
            // Return a dummy on other platforms
            return new CredAdvapi32() {
                @Override
                public boolean CredRead(String targetName, int type, int flags, PCREDENTIAL pcredential) throws LastErrorException {
                    return false;
                }

                @Override
                public boolean CredWrite(CREDENTIAL credential, int flags) throws LastErrorException {
                    return false;
                }

                @Override
                public boolean CredDelete(String targetName, int type, int flags) throws LastErrorException {
                    return false;
                }

                @Override
                public void CredFree(Pointer credential) throws LastErrorException {

                }
            };
        }
    }
}
