import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import dalvik.system.DexClassLoader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * Shell-only, offline installed-APK contract probe. Not an app feature or an inference engine.
 * Args: installed read-only base.apk, /data/local/tmp/p57-UNIQUE fixture directory.
 * Inputs: config.json and the four *-original.png files emitted by P57ServerImageContractTest.
 * Every source pixel must match the synthetic quadrant fixture; arbitrary photos are rejected.
 * No Context, PackageManager, app-private storage, network, token, or provider call is used.
 * Outputs are created in a new output-UUID subdirectory; existing evidence is never overwritten.
 */
public final class OutfitContractProbe {
    private static final String APP_ID = "com.jeiu.fashionproject.preview.qa";
    private static final String PREFIX = "com.jeiu.fashionproject.data.";
    private static final String[] NAMES = {"person", "tops", "bottoms", "shoes"};
    private static final String[] ROLE_NAMES = {"TOP", "BOTTOM", "SHOES"};
    private static final int[][] SIZES = {{1024, 1353}, {930, 1200}, {850, 1200}, {1200, 704}};
    private static final int[][] OLD_SIZES = {{512, 676}, {465, 600}, {425, 600}, {600, 352}};
    private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    private static String stage = "arguments";
    private static File output;
    private final ClassLoader loader;
    private final Class<?> imageClass;
    private final Class<?> continuationClass;
    private final Object images;
    private final Method canonical;
    private final Object emptyContext;
    private final Object suspended;

    private OutfitContractProbe(File apk, File outputDir) throws Exception {
        // APK is installed/read-only; the shell owns only this new diagnostics directory.
        loader = new DexClassLoader(apk.getPath(), outputDir.getPath(), null,
                OutfitContractProbe.class.getClassLoader());
        imageClass = type("DeviceImage");
        continuationClass = loader.loadClass("kotlin.coroutines.Continuation");
        Class<?> imagesClass = type("OnDeviceVtonImages");
        images = imagesClass.getField("INSTANCE").get(null);
        canonical = imagesClass.getDeclaredMethod("canonicalImage", byte[].class, int.class, continuationClass);
        canonical.setAccessible(true);
        emptyContext = loader.loadClass("kotlin.coroutines.EmptyCoroutineContext").getField("INSTANCE").get(null);
        Method suspendedMethod = loader.loadClass("kotlin.coroutines.intrinsics.IntrinsicsKt")
                .getMethod("getCOROUTINE_SUSPENDED");
        suspendedMethod.setAccessible(true);
        suspended = suspendedMethod.invoke(null);
    }

    public static void main(String[] args) {
        try {
            require(args.length == 2, "expected_apk_and_fixture_directory");
            File directory = new File(args[1]).getCanonicalFile();
            require(directory.getPath().matches("/data/local/tmp/p57-[A-Za-z0-9_-]+"), "fixture_path_not_allowed");
            require(directory.isDirectory(), "fixture_directory_missing");
            require(directory.getPath().equals(new File(args[1]).getAbsolutePath()), "fixture_symlink_or_alias");
            File apk = new File(args[0]).getCanonicalFile();
            require(apk.getPath().startsWith("/data/app/") && apk.getName().equals("base.apk")
                    && apk.getParentFile().getName().startsWith(APP_ID + "-"), "not_installed_qa_base_apk");
            require(apk.isFile() && apk.canRead() && !apk.canWrite(), "installed_apk_not_read_only");
            output = new File(directory, "output-" + UUID.randomUUID());
            require(output.mkdir(), "new_output_directory_failed");
            stage = "load_installed_apk";
            new OutfitContractProbe(apk, output).run(apk, directory);
        } catch (Throwable failure) {
            Throwable cause = unwrap(failure);
            String code = cause instanceof ProbeFailure ? cause.getMessage() : "probe_execution_error";
            try {
                if (output != null) writeNew(new File(output, "failure.json"), new JSONObject()
                        .put("status", "FAIL").put("stage", stage).put("code", code)
                        .put("exception_class", cause.getClass().getName()).put("inference_executed", false)
                        .toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { /* Do not overwrite or dump raw exception contents. */ }
            System.err.println("P57_INSTALLED_CONTRACT FAIL stage=" + stage + " code=" + code
                    + " exception=" + cause.getClass().getName());
            System.exit(1);
        }
    }

    private void run(File apk, File directory) throws Exception {
        long started = System.nanoTime();
        Class<?> build = loader.loadClass("com.jeiu.fashionproject.BuildConfig");
        require(APP_ID.equals(build.getField("APPLICATION_ID").get(null)), "unexpected_application_id");
        require(build.getField("VERSION_CODE").getInt(null) == 18611, "unexpected_installed_version");
        require(build.getClassLoader() == loader && imageClass.getClassLoader() == loader,
                "classes_not_loaded_from_installed_apk");
        String sourceSha = (String) build.getField("GIT_SHA").get(null);
        require(sourceSha.matches("[a-f0-9]{40}"), "invalid_embedded_source_sha");
        JSONObject receipt = new JSONObject().put("synthetic_only", true).put("actual_device", true)
                .put("inference_executed", false).put("app_ui_e2e", false)
                .put("scope", "installed APK image normalization and request serialization; not UI or server E2E")
                .put("application_id", APP_ID).put("version_code", 18611).put("source_sha", sourceSha)
                .put("embedded_git_sha", sourceSha)
                .put("apk_sha256", hashFile(apk)).put("apk_bytes", apk.length());

        stage = "parse_config";
        byte[] configBytes = readInput(directory, "config.json", 65536);
        JSONObject configJson = new JSONObject(new String(configBytes, StandardCharsets.UTF_8));
        Class<?> repoClass = type("ServerOutfitVtonRepository");
        Object repo = repoClass.getField("INSTANCE").get(null);
        Object config = repoClass.getMethod("parseConfig", JSONObject.class).invoke(repo, configJson);
        require((Boolean) call(config, "getReady"), "fixture_config_not_ready");
        require("synthetic-policy".equals(call(config, "getPolicy"))
                && "synthetic-p57".equals(call(config, "getRevision"))
                && "synthetic-wire-model".equals(call(config, "getModelId")), "only_synthetic_config_allowed");
        int maxSide = (Integer) call(config, "getMaxInputSide");
        require(maxSide == 2048, "expected_server_2048_contract");
        Object model = call(config, "imageSpec");
        require((Integer) call(model, "getMaxSide") == maxSide, "image_spec_mismatch");
        receipt.put("config_sha256", hash(configBytes)).put("max_input_side", maxSide)
                .put("model_id", call(model, "getId")).put("model_revision", call(model, "getRevision"))
                .put("model_sha256", call(model, "getSha256"));

        Class<?> roleClass = type("DeviceGarmentRole");
        Class<?> garmentClass = type("DeviceGarmentInput");
        Class<?> selectionClass = type("DeviceVtonSelection");
        List<Object> selectedGarments = new ArrayList<>();
        Map<Object, Object> garments = new LinkedHashMap<>();
        Object person = null;
        JSONObject records = new JSONObject();
        JSONObject oldRecords = new JSONObject();
        Constructor<?> garmentConstructor = garmentClass.getConstructor(String.class, roleClass, String.class, String.class);
        for (int i = 0; i < NAMES.length; i++) {
            String name = NAMES[i];
            stage = "synthetic_input_" + name;
            byte[] original = readInput(directory, name + "-original.png", MAX_INPUT_BYTES);
            List<String> originalChunks = chunks(original);
            require(!originalChunks.contains("eXIf"), "base_fixture_must_not_be_rotated");
            PixelInfo source = inspect(original, SIZES[i][0], SIZES[i][1], false, true);
            stage = "canonical_2048_" + name;
            Object image = canonical(original, maxSide);
            byte[] wire = imageBytes(image);
            assertImageSize(image, SIZES[i][0], SIZES[i][1]);
            PixelInfo canonicalPixels = inspect(wire, SIZES[i][0], SIZES[i][1], false, true);
            require(source.rgbaSha.equals(canonicalPixels.rgbaSha), "canonical_rgba_changed_" + name);
            require(hash(wire).equals(call(image, "getSha256")), "canonical_sha_mismatch");
            require(!chunks(wire).contains("eXIf"), "canonical_exif_not_removed");
            writeNew(new File(output, name + "-original.png"), original);
            writeNew(new File(output, name + "-wire.png"), wire);
            records.put(name, new JSONObject().put("width", SIZES[i][0]).put("height", SIZES[i][1])
                    .put("original_sha256", hash(original)).put("wire_sha256", hash(wire))
                    .put("rgba_sha256", canonicalPixels.rgbaSha)
                    .put("original_file_sha256", hash(original)).put("canonical_file_sha256", hash(wire))
                    .put("original_rgba_sha256", source.rgbaSha).put("canonical_rgba_sha256", canonicalPixels.rgbaSha)
                    .put("original_png_chunks", new JSONArray(originalChunks)).put("rgba_equal", true));

            stage = "legacy_1024_" + name;
            Object old = canonical(original, 1024);
            assertImageSize(old, OLD_SIZES[i][0], OLD_SIZES[i][1]);
            byte[] oldBytes = imageBytes(old);
            PixelInfo oldPixels = inspect(oldBytes, OLD_SIZES[i][0], OLD_SIZES[i][1], false, false);
            require(!source.rgbaSha.equals(oldPixels.rgbaSha), "legacy_resize_not_reproduced");
            writeNew(new File(output, name + "-legacy1024.png"), oldBytes);
            oldRecords.put(name, new JSONObject().put("width", OLD_SIZES[i][0]).put("height", OLD_SIZES[i][1])
                    .put("file_sha256", hash(oldBytes)).put("rgba_sha256", oldPixels.rgbaSha));
            if (i == 0) person = image;
            else {
                Object role = roleClass.getMethod("valueOf", String.class).invoke(null, ROLE_NAMES[i - 1]);
                garments.put(role, image);
                selectedGarments.add(garmentConstructor.newInstance("synthetic-" + name, role,
                        Uri.fromFile(inputFile(directory, name + "-original.png")).toString(),
                        "400,600,200,300,80,1,0,1"));
            }
        }

        stage = "actual_request_body";
        String requestId = UUID.randomUUID().toString();
        Object selection = selectionClass.getConstructor(String.class, long.class, List.class).newInstance(
                Uri.fromFile(inputFile(directory, "person-original.png")).toString(), 57L, selectedGarments);
        Class<?> modelClass = type("DeviceModelSpec");
        Method validate = images.getClass().getMethod("validateSelection$app", String.class, selectionClass, modelClass);
        validate.invoke(images, requestId, selection, model);
        Method digest = images.getClass().getDeclaredMethod("receiptDigest", String.class, selectionClass,
                modelClass, imageClass, Map.class);
        digest.setAccessible(true);
        String inputDigest = (String) digest.invoke(images, requestId, selection, model, person, garments);
        Class<?> jobClass = type("DevicePreparedJob");
        Object job = jobClass.getConstructor(String.class, selectionClass, modelClass, imageClass, Map.class, String.class)
                .newInstance(requestId, selection, model, person, garments, inputDigest);
        byte[] payload = (byte[]) repoClass.getMethod("requestBody", jobClass, type("OutfitServerConfig"))
                .invoke(repo, job, config);
        JSONObject body = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        require(requestId.equals(body.getString("request_id")), "serialized_request_id_mismatch");
        require(Arrays.equals(imageBytes(person), dataImage(body.getString("person"))), "person_wire_mismatch");
        JSONArray bodyGarments = body.getJSONArray("garments");
        require(bodyGarments.length() == 3, "garment_count_mismatch");
        int index = 0;
        for (Object image : garments.values()) {
            JSONObject garment = bodyGarments.getJSONObject(index);
            require(NAMES[index + 1].equals(garment.getString("category")), "garment_role_mismatch");
            require(Arrays.equals(imageBytes(image), dataImage(garment.getString("image"))), "garment_wire_mismatch");
            index++;
        }
        writeNew(new File(output, "payload.json"), payload);
        receipt.put("request_id", requestId).put("input_receipt_sha256", inputDigest)
                .put("body_sha256", hash(payload)).put("payload_sha256", hash(payload)).put("payload_bytes", payload.length)
                .put("images", records).put("legacy_1024_reproduction", oldRecords);

        stage = "exif_rotation_and_reopen";
        receipt.put("rotation", rotationDiagnostic(readInput(directory, "shoes-original.png", MAX_INPUT_BYTES), maxSide));
        receipt.put("status", "PASS").put("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
        byte[] receiptBytes = receipt.toString(2).getBytes(StandardCharsets.UTF_8);
        writeNew(new File(output, "receipt.json"), receiptBytes);
        writeNew(new File(output, "receipt.sha256"), (hash(receiptBytes) + "  receipt.json\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("P57_INSTALLED_CONTRACT PASS output=" + output.getPath()
                + " payload_sha256=" + hash(payload) + " source_sha=" + sourceSha);
    }

    private JSONObject rotationDiagnostic(byte[] source, int maxSide) throws Exception {
        byte[] tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8)
                .putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1)
                .putShort((short) 6).putShort((short) 0).putInt(0).array();
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.write(source, 0, 33); joined.write(pngChunk("eXIf", tiff)); joined.write(source, 33, source.length - 33);
        byte[] rotatedInput = joined.toByteArray();
        require(chunks(rotatedInput).contains("eXIf"), "rotation_fixture_missing_exif");
        Object rotated = canonical(rotatedInput, maxSide);
        assertImageSize(rotated, 704, 1200);
        byte[] rotatedBytes = imageBytes(rotated);
        PixelInfo first = inspect(rotatedBytes, 704, 1200, true, true);
        require(!chunks(rotatedBytes).contains("eXIf"), "rotation_exif_not_removed");
        Object reopened = canonical(rotatedBytes, maxSide);
        assertImageSize(reopened, 704, 1200);
        byte[] reopenedBytes = imageBytes(reopened);
        PixelInfo second = inspect(reopenedBytes, 704, 1200, true, true);
        require(first.rgbaSha.equals(second.rgbaSha), "reopen_rotates_or_changes_pixels");
        require(!chunks(reopenedBytes).contains("eXIf"), "reopen_exif_reintroduced");
        writeNew(new File(output, "rotation-input.png"), rotatedInput);
        writeNew(new File(output, "rotation-canonical.png"), rotatedBytes);
        writeNew(new File(output, "rotation-reopened.png"), reopenedBytes);
        return new JSONObject().put("width", 704).put("height", 1200).put("exif_removed", true)
                .put("independent_clockwise_pixels", true).put("reopen_rgba_equal", true)
                .put("input_sha256", hash(rotatedInput)).put("canonical_sha256", hash(rotatedBytes))
                .put("reopened_sha256", hash(reopenedBytes)).put("rgba_sha256", first.rgbaSha);
    }

    private Object canonical(byte[] bytes, int side) throws Exception {
        CountDownLatch resumed = new CountDownLatch(1);
        Object[] result = new Object[1];
        Object continuation = Proxy.newProxyInstance(loader, new Class<?>[]{continuationClass}, (proxy, method, args) -> {
            if (method.getName().equals("getContext")) return emptyContext;
            if (method.getName().equals("resumeWith")) { result[0] = args[0]; resumed.countDown(); return null; }
            if (method.getName().equals("toString")) return "SyntheticProbeContinuation";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new ProbeFailure("unexpected_continuation_method");
        });
        Object value = canonical.invoke(images, bytes, side, continuation);
        if (value == suspended) {
            require(resumed.await(10, TimeUnit.SECONDS), "canonical_suspension_timeout");
            value = result[0];
            loader.loadClass("kotlin.ResultKt").getMethod("throwOnFailure", Object.class).invoke(null, value);
        }
        require(imageClass.isInstance(value), "canonical_return_type_mismatch");
        return value;
    }

    private static int marker(int x, int y, int width, int height) {
        if (y < height / 2) return x < width / 2 ? 0xffd02030 : 0xff30b040;
        return x < width / 2 ? 0xff2040d0 : 0xffe0b030;
    }

    private static PixelInfo inspect(byte[] bytes, int width, int height, boolean rotated, boolean verifyMarkers) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        require(bounds.outWidth == width && bounds.outHeight == height, "bounded_fixture_dimensions_mismatch");
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        require(bitmap != null, "png_decode_failed");
        try {
            require(bitmap.getWidth() == width && bitmap.getHeight() == height, "decoded_dimensions_mismatch");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int[] row = new int[width]; byte[] rgba = new byte[width * 4];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int pixel = row[x];
                    if (verifyMarkers) {
                        int expected = rotated ? marker(y, 703 - x, 1200, 704) : marker(x, y, width, height);
                        require(pixel == expected, "not_exact_synthetic_marker_or_pixel_changed");
                    }
                    rgba[4 * x] = (byte) (pixel >>> 16); rgba[4 * x + 1] = (byte) (pixel >>> 8);
                    rgba[4 * x + 2] = (byte) pixel; rgba[4 * x + 3] = (byte) (pixel >>> 24);
                }
                digest.update(rgba);
            }
            return new PixelInfo(hex(digest.digest()));
        } finally { bitmap.recycle(); }
    }

    private static List<String> chunks(byte[] bytes) throws Exception {
        require(bytes.length >= 33 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10}), "invalid_png_signature");
        List<String> types = new ArrayList<>(); int offset = 8;
        while (offset < bytes.length) {
            require(bytes.length - offset >= 12, "truncated_png_header");
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            require(length >= 0 && length <= bytes.length - offset - 12, "invalid_png_length");
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32(); crc.update(bytes, offset + 4, length + 4);
            long expected = ByteBuffer.wrap(bytes, offset + length + 8, 4).getInt() & 0xffffffffL;
            require(crc.getValue() == expected, "invalid_png_crc");
            require(!types.contains("IEND"), "png_data_after_iend");
            types.add(type); offset += length + 12;
        }
        require(types.get(0).equals("IHDR") && types.get(types.size() - 1).equals("IEND")
                && types.contains("IDAT"), "invalid_png_structure");
        return types;
    }

    private static byte[] pngChunk(String type, byte[] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] tag = type.getBytes(StandardCharsets.US_ASCII);
            out.writeInt(data.length); out.write(tag); out.write(data);
            CRC32 crc = new CRC32(); crc.update(tag); crc.update(data); out.writeInt((int) crc.getValue());
        }
        return bytes.toByteArray();
    }

    private Class<?> type(String name) throws Exception { return loader.loadClass(PREFIX + name); }
    private static Object call(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static byte[] imageBytes(Object image) throws Exception { return (byte[]) call(image, "getBytes"); }
    private static void assertImageSize(Object image, int width, int height) throws Exception {
        require((Integer) call(image, "getWidth") == width && (Integer) call(image, "getHeight") == height,
                "prepared_dimensions_mismatch");
    }
    private static byte[] dataImage(String value) throws Exception {
        require(value.startsWith("data:image/png;base64,"), "unexpected_wire_image_type");
        return Base64.decode(value.substring("data:image/png;base64,".length()), Base64.DEFAULT);
    }
    private static File inputFile(File directory, String name) throws Exception {
        File value = new File(directory, name);
        require(value.getCanonicalFile().getParentFile().equals(directory) && value.isFile(), "input_symlink_or_missing");
        return value;
    }
    private static byte[] readInput(File directory, String name, int limit) throws Exception {
        File value = inputFile(directory, name);
        require(value.length() > 0 && value.length() <= limit, "input_size_limit");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(value)) {
            byte[] buffer = new byte[16384]; int read;
            while ((read = in.read(buffer)) != -1) {
                require(read <= limit - out.size(), "input_grew_past_limit"); out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }
    private static void writeNew(File file, byte[] bytes) throws Exception {
        require(file.getCanonicalFile().getParentFile().equals(output), "output_path_not_allowed");
        require(file.createNewFile(), "output_already_exists");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
    }
    private static String hash(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String hashFile(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static Throwable unwrap(Throwable value) {
        while (value instanceof InvocationTargetException && ((InvocationTargetException)value).getCause() != null)
            value = ((InvocationTargetException)value).getCause();
        return value;
    }
    private static void require(boolean condition, String code) throws ProbeFailure { if (!condition) throw new ProbeFailure(code); }
    private static final class PixelInfo { final String rgbaSha; PixelInfo(String sha) { rgbaSha = sha; } }
    private static final class ProbeFailure extends Exception { ProbeFailure(String code) { super(code); } }
}
