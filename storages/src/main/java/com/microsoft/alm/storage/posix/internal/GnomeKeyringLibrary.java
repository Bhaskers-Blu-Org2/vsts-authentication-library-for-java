// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.storage.posix.internal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * Simple interface to store and retrieve secrets with gnome-keyring
 *
 * https://developer.gnome.org/gnome-keyring/stable/ch01.html
 */
public interface GnomeKeyringLibrary extends Library {

    GnomeKeyringLibrary INSTANCE = (GnomeKeyringLibrary)
                Native.loadLibrary("gnome-keyring", GnomeKeyringLibrary.class);

    /**
     * Save secrets to disk
     */
    public static final String GNOME_KEYRING_DEFAULT = null;

    /**
     * Save secrets in memory
     */
    public static final String GNOME_KEYRING_SESSION = "session";

    /**
     * GnomeKeyringResult:
     *  GNOME_KEYRING_RESULT_OK,
     *  GNOME_KEYRING_RESULT_DENIED,
     *  GNOME_KEYRING_RESULT_NO_KEYRING_DAEMON,
     *  GNOME_KEYRING_RESULT_ALREADY_UNLOCKED,
     *  GNOME_KEYRING_RESULT_NO_SUCH_KEYRING,
     *  GNOME_KEYRING_RESULT_BAD_ARGUMENTS,
     *  GNOME_KEYRING_RESULT_IO_ERROR,
     *  GNOME_KEYRING_RESULT_CANCELLED,
     *  GNOME_KEYRING_RESULT_KEYRING_ALREADY_EXISTS,
     *  GNOME_KEYRING_RESULT_NO_MATCH
     */
    public static final int GNOME_KEYRING_RESULT_OK                     = 0;
    public static final int GNOME_KEYRING_RESULT_DENIED                 = 1;
    public static final int GNOME_KEYRING_RESULT_NO_KEYRING_DAEMON      = 2;
    public static final int GNOME_KEYRING_RESULT_ALREADY_UNLOCKED       = 3;
    public static final int GNOME_KEYRING_RESULT_NO_SUCH_KEYRING        = 4;
    public static final int GNOME_KEYRING_RESULT_BAD_ARGUMENTS          = 5;
    public static final int GNOME_KEYRING_RESULT_IO_ERROR               = 6;
    public static final int GNOME_KEYRING_RESULT_CANCELLED              = 7;
    public static final int GNOME_KEYRING_RESULT_KEYRING_ALREADY_EXISTS = 8;
    public static final int GNOME_KEYRING_RESULT_NO_MATCH               = 9;

    /**
     * The item types
     *  GNOME_KEYRING_ITEM_GENERIC_SECRET = 0,
     *  GNOME_KEYRING_ITEM_NETWORK_PASSWORD,
     *  GNOME_KEYRING_ITEM_NOTE,
     *  GNOME_KEYRING_ITEM_CHAINED_KEYRING_PASSWORD,
     *  GNOME_KEYRING_ITEM_ENCRYPTION_KEY_PASSWORD,
     *
     *  GNOME_KEYRING_ITEM_PK_STORAGE = 0x100,
     *
     * Not used, remains here only for compatibility
     *  GNOME_KEYRING_ITEM_LAST_TYPE,
     */
    public static final int GNOME_KEYRING_ITEM_GENERIC_SECRET           = 0;
    public static final int GNOME_KEYRING_ITEM_NETWORK_PASSWORD         = 1;
    public static final int GNOME_KEYRING_ITEM_NOTE                     = 2;
    public static final int GNOME_KEYRING_ITEM_CHAINED_KEYRING_PASSWORD = 3;
    public static final int GNOME_KEYRING_ITEM_ENCRYPTION_KEY_PASSWORD  = 4;

    /**
     * GnomeKeyringAttributeType:
     *   GNOME_KEYRING_ATTRIBUTE_TYPE_STRING,
     *   GNOME_KEYRING_ATTRIBUTE_TYPE_UINT32
     */
    public static final int GNOME_KEYRING_ATTRIBUTE_TYPE_STRING         = 0;
    public static final int GNOME_KEYRING_ATTRIBUTE_TYPE_UINT32         = 1;


    /**
     * Item Attributes — Attributes of individual keyring items.
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Item-Attributes.html
     */
    public static class GnomeKeyringPasswordSchemaAttribute extends Structure {

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(new String[]{
                    "name",
                    "type"
            });
        }

        public String name;

        public int type;
    }

    /**
     * Schema for secret
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Simple-Password-Storage.html#GnomeKeyringPasswordSchema
     */
    public static class GnomeKeyringPasswordSchema extends Structure {

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(new String[]{
                    "item_type",
                    "attributes"
            });
        }

        public int item_type;

        public GnomeKeyringPasswordSchemaAttribute[] attributes = new GnomeKeyringPasswordSchemaAttribute[32];
    }

    /**
     * A pointer to pointer helper structure
     */
    public static class PointerToPointer extends Structure {

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(new String[] {
                    "pointer"
            });
        }

        public Pointer pointer;
    }

    /**
     * Storing a secret, without paraphrasing, please read:
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Simple-Password-Storage.html#gnome-keyring-store-password-sync
     *
     * @param schema
     *      schema for the secret
     * @param keyring
     *      "session" means in memory; {@code null} for default on disk storage
     * @param display_name
     *      display name of this secret
     * @param password
     *      actual password
     * @param args
     *      varargs, attributes of the secret, please read the API document
     *
     * @return
     *      return code
     */
    public int gnome_keyring_store_password_sync(final GnomeKeyringPasswordSchema schema,
                                                 final String keyring,
                                                 final String display_name,
                                                 final String password,
                                                 Object... args);

    /**
     * Retrieving a stored secret, without paraphrasing, please read:
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Simple-Password-Storage.html#gnome-keyring-find-password-sync
     *
     * @param schema
     *      schema for the secret
     * @param pPassword
     *      pointer to pointer of the retrieved secret
     * @param args
     *      varargs used to locate the secret
     *
     * @return
     *      return code
     */
    public int gnome_keyring_find_password_sync(final GnomeKeyringPasswordSchema schema,
                                                final PointerToPointer pPassword,
                                                Object... args);

    /**
     * Delete a stored secret, without paraphrasing, please read:
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Simple-Password-Storage.html#gnome-keyring-delete-password-sync
     *
     * @param schema
     *      schema for the secret
     * @param args
     *      varargs used to locate the secret
     *
     * @return
     *      return code
     */
    public int gnome_keyring_delete_password_sync(final GnomeKeyringPasswordSchema schema,
                                                  Object... args);


    /**
     * Free the in memory secret pointer, without paraphrasing, please read:
     *
     * https://developer.gnome.org/gnome-keyring/stable/gnome-keyring-Simple-Password-Storage.html#gnome-keyring-free-password
     *
     * @param password
     *      pointer to the secret to be freed
     */
    public void gnome_keyring_free_password(final Pointer password);
}
