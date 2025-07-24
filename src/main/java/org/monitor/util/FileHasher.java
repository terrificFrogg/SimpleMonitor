package org.monitor.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public class FileHasher {
    public static Optional<String> hashFile(File file, String algorithm){
        MessageDigest digest = null;
        Optional<String> optionalHash = Optional.empty();
        try {
            digest = MessageDigest.getInstance(algorithm);
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return optionalHash;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
