package net.minecraft.client.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Pack zips as people download them: wrapped in a folder, or inside another zip. */
final class FileResourcePackTest {
    private static final byte[] MCMETA = "{\"pack\":{\"pack_format\":1,\"description\":\"Test\"}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = {1, 2, 3};

    @TempDir Path folder;

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private Path write(String name, Map<String, byte[]> entries) throws IOException {
        return Files.write(folder.resolve(name), zip(entries));
    }

    @Test
    void readsAPackWrappedInAFolder() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Plastic Craft/pack.mcmeta", MCMETA);
        entries.put("Plastic Craft/assets/minecraft/textures/blocks/dirt.png", PNG);
        entries.put("__MACOSX/Plastic Craft/._pack.mcmeta", PNG);
        try (FileResourcePack pack = new FileResourcePack(write("plastic.zip", entries))) {
            assertTrue(pack.has("pack.mcmeta"));
            assertTrue(pack.has("assets/minecraft/textures/blocks/dirt.png"));
            assertArrayEquals(PNG, pack.open("assets/minecraft/textures/blocks/dirt.png").readAllBytes());
        }
    }

    @Test
    void readsAPackZipInsideADownloadZip() throws IOException {
        Map<String, byte[]> inner = new LinkedHashMap<>();
        inner.put("pack.mcmeta", MCMETA);
        inner.put("assets/minecraft/textures/blocks/dirt.png", PNG);
        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("00 - Copy to game folder\\resourcepacks\\Dem.zip", zip(inner));
        outer.put("Readme - en.txt", PNG);
        try (FileResourcePack pack = new FileResourcePack(write("dem.zip", outer))) {
            assertEquals("dem.zip", pack.name());
            assertTrue(pack.has("pack.mcmeta"));
            assertArrayEquals(PNG, pack.open("assets/minecraft/textures/blocks/dirt.png").readAllBytes());
        }
    }

    @Test
    void listsWrappedPacksInTheRepository() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Wrapped/pack.mcmeta", MCMETA);
        write("wrapped.zip", entries);
        ResourcePackRepository repository = new ResourcePackRepository(new ClasspathResourceManager());
        repository.setDirectory(folder);
        var found = repository.scan();
        assertEquals(1, found.size());
        assertEquals("Test", found.get(0).description);
    }
}
