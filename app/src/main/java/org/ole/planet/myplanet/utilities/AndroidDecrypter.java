package org.ole.planet.myplanet.utilities;

import android.util.Log;

import com.github.kittinunf.fuel.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.rtner.misc.BinTools;
import de.rtner.security.auth.spi.PBKDF2Engine;
import de.rtner.security.auth.spi.PBKDF2Parameters;

public class AndroidDecrypter {

    public static final String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 1)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static byte[] encrypt(String plainText, String key, String iv) throws Exception {
        byte[] clean = plainText.getBytes();
        // Generating IV.\n
        int ivSize = 16;
        byte[] ivBytes = new byte[ivSize];
        System.arraycopy(hexStringToByteArray(iv), 0, ivBytes, 0, ivBytes.length);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
        // Hashing key.\n
        byte[] keyBytes = new byte[32];
        System.arraycopy(hexStringToByteArray(key), 0, keyBytes, 0, keyBytes.length);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        // Encrypt.\n
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
        byte[] encrypted = cipher.doFinal(clean);
        // Combine IV and encrypted part.
        byte[] encryptedIVAndText = new byte[ivSize + encrypted.length];
        System.arraycopy(ivBytes, 0, encryptedIVAndText, 0, ivSize);
        System.arraycopy(encrypted, 0, encryptedIVAndText, ivSize, encrypted.length);

        return encrypted;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] hashInBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public static String decrypt(String encrypted, String key, String iv) throws Exception {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(hexStringToByteArray(iv));

            Cipher ecipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            ecipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);

            byte[] raw = Base64.decode(encrypted, Base64.DEFAULT);

            byte[] originalBytes = ecipher.doFinal(raw);

            String original = new String(originalBytes, "UTF8");

            return original;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }


    public Boolean AndroidDecrypter(String usr_ID, String usr_rawPswd, String db_PswdkeyValue, String db_Salt) {
        try {

            //SecureRandom.getInstance("HmacSHA1").nextBytes(salt);
            PBKDF2Parameters p = new PBKDF2Parameters("HmacSHA1", "utf-8", db_Salt.getBytes(), 10);
            ///byte[] dk = new PBKDF2Engine(p).deriveKey("password", 20);
            byte[] dk = new PBKDF2Engine(p).deriveKey(usr_rawPswd, 20);
            System.out.println(usr_ID + " Value " + BinTools.bin2hex(dk).toLowerCase());
            if (db_PswdkeyValue.equals(BinTools.bin2hex(dk).toLowerCase())) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}