package ai.velr.internal;

import ai.velr.VelrException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class NativeLibrary {
    private static boolean loaded;

    private NativeLibrary() {}

    static synchronized void load() {
        if (loaded) {
            return;
        }

        if (isAndroid()) {
            System.loadLibrary("velr_jni");
            loaded = true;
            return;
        }

        String resource = resourcePath();
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new VelrException("shipped Velr JNI library is missing: " + resource);
            }
            byte[] bytes = readAll(in);
            File file = materialize(resource, bytes);
            System.load(file.getAbsolutePath());
            loaded = true;
        } catch (IOException e) {
            throw new VelrException("failed to load shipped Velr JNI library", e);
        }
    }

    private static boolean isAndroid() {
        String vm = System.getProperty("java.vm.name", "");
        if (vm.toLowerCase().contains("dalvik")) {
            return true;
        }
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static String resourcePath() {
        String os = normalizeOs(System.getProperty("os.name", ""));
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        String file = System.mapLibraryName("velr_jni");
        return "/ai/velr/native/" + os + "-" + arch + "/" + file;
    }

    private static String normalizeOs(String os) {
        String value = os.toLowerCase();
        if (value.contains("mac") || value.contains("darwin")) {
            return "macos";
        }
        if (value.contains("win")) {
            return "windows";
        }
        if (value.contains("linux")) {
            return "linux";
        }
        throw new VelrException("unsupported Velr JVM operating system: " + os);
    }

    private static String normalizeArch(String arch) {
        String value = arch.toLowerCase();
        if (value.equals("x86_64") || value.equals("amd64")) {
            return "x86_64";
        }
        if (value.equals("aarch64") || value.equals("arm64")) {
            return "aarch64";
        }
        throw new VelrException("unsupported Velr JVM architecture: " + arch);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static File materialize(String resource, byte[] bytes) throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "velr-jni");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("failed to create " + dir);
        }
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        File out = new File(dir, sha256(bytes) + "-" + fileName);
        if (out.isFile() && out.length() == bytes.length) {
            return out;
        }
        File tmp = new File(dir, out.getName() + ".tmp-" + System.nanoTime());
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(bytes);
        }
        if (!tmp.renameTo(out) && !out.isFile()) {
            throw new IOException("failed to move " + tmp + " to " + out);
        }
        return out;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
