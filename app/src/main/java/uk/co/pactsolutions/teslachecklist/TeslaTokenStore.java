package uk.co.pactsolutions.teslachecklist;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class TeslaTokenStore {
    private static final String KEY_ALIAS = "tsure_tesla_tokens";
    private static final String PREFS = "tesla_connection";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private static final String EXPIRES = "expires";

    private final SharedPreferences prefs;

    TeslaTokenStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isConnected() {
        return !prefs.getString(REFRESH, "").isEmpty();
    }

    synchronized void save(String accessToken, String refreshToken, long expiresAt) throws Exception {
        prefs.edit()
            .putString(ACCESS, encrypt(accessToken))
            .putString(REFRESH, encrypt(refreshToken))
            .putLong(EXPIRES, expiresAt)
            .apply();
    }

    synchronized String accessToken() throws Exception {
        return decrypt(prefs.getString(ACCESS, ""));
    }

    synchronized String refreshToken() throws Exception {
        return decrypt(prefs.getString(REFRESH, ""));
    }

    long expiresAt() {
        return prefs.getLong(EXPIRES, 0L);
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return generator.generateKey();
    }

    private String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String stored) throws Exception {
        if (stored == null || stored.isEmpty()) return "";
        String[] parts = stored.split("\\.", 2);
        if (parts.length != 2) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        );
        return new String(
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
            StandardCharsets.UTF_8
        );
    }
}
