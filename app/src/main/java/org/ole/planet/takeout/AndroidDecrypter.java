package org.ole.planet.takeout;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import de.rtner.misc.BinTools;
import de.rtner.security.auth.spi.PBKDF2Engine;
import de.rtner.security.auth.spi.PBKDF2Parameters;

public class AndroidDecrypter {

    Boolean AndroidDecrypter(String usr_ID,String usr_rawPswd, String db_PswdkeyValue){
        ///"56942958ed379a8c7d4a03a701814561"

        ///////////// Library 1 //////
        byte[] salt = null;
        try {
            ///String md5Val = md5("leomaxi");
            salt = md5(usr_ID).getBytes();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        try {

                //SecureRandom.getInstance("HmacSHA1").nextBytes(salt);
                PBKDF2Parameters p = new PBKDF2Parameters("HmacSHA1", "utf-8", salt, 10);
                ///byte[] dk = new PBKDF2Engine(p).deriveKey("password", 20);
                byte[] dk = new PBKDF2Engine(p).deriveKey(usr_rawPswd, 20);
                System.out.println( usr_ID +" Value "+BinTools.bin2hex(dk).toLowerCase());
                if(db_PswdkeyValue.equals(BinTools.bin2hex(dk).toLowerCase())){
                    return true;
                }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


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
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}