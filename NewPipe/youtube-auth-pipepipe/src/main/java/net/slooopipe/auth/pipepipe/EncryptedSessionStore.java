// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.pipepipe;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import net.slooopipe.auth.api.AuthDiagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore key + noBackupFilesDir: no credentials in normal app preferences/exports. */
final class EncryptedSessionStore {
    private final AtomicFile file;
    private static final String ALIAS = "slooopipe.youtube.session.v1";

    EncryptedSessionStore(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "youtube-session-v1"));
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            AuthDiagnostics.event("storage.keystore_create");
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(
                    new KeyGenParameterSpec.Builder(
                                    ALIAS,
                                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    synchronized String read() throws Exception {
        AuthDiagnostics.event("storage.read_begin", "present", file.getBaseFile().exists());
        if (!file.getBaseFile().exists()) {
            return null;
        }
        ByteBuffer input = ByteBuffer.wrap(file.readFully());
        int size = input.get() & 0xff;
        if (size != 12 || input.remaining() < size + 16) {
            throw new IllegalStateException();
        }
        byte[] iv = new byte[size];
        input.get(iv);
        byte[] encrypted = new byte[input.remaining()];
        input.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        final String decoded = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        AuthDiagnostics.event("storage.read_end");
        return decoded;
    }

    synchronized void write(String data) throws Exception {
        AuthDiagnostics.event("storage.write_begin");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        FileOutputStream output = file.startWrite();
        try {
            output.write(iv.length);
            output.write(iv);
            output.write(encrypted);
            file.finishWrite(output);
            AuthDiagnostics.event("storage.write_end");
        } catch (Exception error) {
            AuthDiagnostics.failure("storage.write_failed", error);
            file.failWrite(output);
            throw error;
        }
    }

    synchronized void clear() {
        file.delete();
        AuthDiagnostics.event("storage.cleared");
    }
}
