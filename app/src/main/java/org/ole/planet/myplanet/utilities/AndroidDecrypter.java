package org.ole.planet.myplanet.utilities;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
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

    public static byte[] encrypt(String s) throws Exception {
        return encryptDecrypt(s, Cipher.ENCRYPT_MODE);
    }

    private static byte[] encryptDecrypt(String s, int mode) throws Exception {
        byte[] keyBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2};
        String algorithm = "0123456789abcdef";
        SecretKeySpec key = new SecretKeySpec(keyBytes, algorithm);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(mode, key);
        return cipher.doFinal(s.getBytes());
    }

    public static byte[] decrypt(String s) throws Exception {
        return encryptDecrypt(s, Cipher.DECRYPT_MODE);
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