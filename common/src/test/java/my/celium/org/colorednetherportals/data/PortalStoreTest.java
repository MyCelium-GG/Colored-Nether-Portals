package my.celium.org.colorednetherportals.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import my.celium.org.colorednetherportals.data.PortalStyle.Mode;

class PortalStoreTest {
    private static final PortalStore.Key HOME = new PortalStore.Key("minecraft:overworld", 10, 64, -3);
    private static final PortalStore.Key NETHER = new PortalStore.Key("minecraft:the_nether", 0, 70, 0);

    @Test
    void vanillaIsNeverStored() {
        PortalStore store = new PortalStore();
        assertTrue(store.put(HOME, PortalStyle.solid(0xFF0000)));
        assertFalse(store.put(HOME, PortalStyle.solid(0xFF0000)), "same style is no change");
        assertTrue(store.put(HOME, PortalStyle.VANILLA), "vanilla removes");
        assertEquals(0, store.size());
        assertEquals(PortalStyle.VANILLA, store.get(HOME));
        assertFalse(store.put(HOME, PortalStyle.VANILLA));
    }

    @Test
    void filtersByDimension() {
        PortalStore store = new PortalStore();
        store.put(HOME, PortalStyle.solid(1));
        store.put(NETHER, PortalStyle.solid(2));
        assertEquals(1, store.in("minecraft:the_nether").size());
        assertEquals(PortalStyle.solid(2), store.in("minecraft:the_nether").get(NETHER));
    }

    @Test
    void jsonRoundTripsAndIsReadable() {
        PortalStore store = new PortalStore();
        store.put(HOME, new PortalStyle(Mode.GRADIENT, 0x3AB3DA, 0xB388FF, 2));
        store.put(NETHER, new PortalStyle(Mode.RAINBOW, 0, 0, 3));
        String json = store.toJson();
        assertTrue(json.contains("\"mode\": \"gradient\""), json);
        assertTrue(json.contains("\"#3AB3DA\""), json);

        PortalStore back = new PortalStore();
        assertEquals(2, back.fromJson(json));
        assertEquals(store.all(), back.all());
    }

    @Test
    void manyColorsRoundTrip() {
        PortalStore store = new PortalStore();
        store.put(HOME, new PortalStyle(Mode.GRADIENT, List.of(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF), 2));
        PortalStore back = new PortalStore();
        back.fromJson(store.toJson());
        assertEquals(List.of(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF), back.get(HOME).colors());
    }

    @Test
    void handEditedFilesAreForgiving() {
        PortalStore store = new PortalStore();
        int read = store.fromJson("""
                {"portals": [
                  {"dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3, "mode": "SOLID", "color": "f00"},
                  {"dimension": "", "x": 0, "y": 0, "z": 0, "mode": "solid", "color": "#00ff00"},
                  {"dimension": "minecraft:overworld", "x": 4, "y": 5, "z": 6, "mode": "vanilla"},
                  {"dimension": "minecraft:overworld", "x": 7, "y": 8, "z": 9, "mode": "pulse", "color": "nope", "speed": 99}
                ]}""");
        assertEquals(2, read);
        PortalStyle solid = store.get(new PortalStore.Key("minecraft:overworld", 1, 2, 3));
        assertEquals(PortalStyle.solid(0xFF0000), solid);
        PortalStyle pulse = store.get(new PortalStore.Key("minecraft:overworld", 7, 8, 9));
        assertEquals(0xFFFFFF, pulse.color());
        assertEquals(3, pulse.speed());
        assertThrows(IllegalArgumentException.class, () -> store.fromJson("{ nope"));
    }

    @Test
    void fileRoundTripAndBrokenFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data/colorednetherportals.json");
        PortalStore store = new PortalStore();
        assertFalse(store.load(file));
        store.put(HOME, PortalStyle.solid(0x123456));
        store.save(file);
        assertFalse(Files.exists(dir.resolve("data/colorednetherportals.json.tmp")));

        PortalStore back = new PortalStore();
        assertTrue(back.load(file));
        assertEquals(PortalStyle.solid(0x123456), back.get(HOME));

        Files.writeString(file, "{ broken");
        assertThrows(IOException.class, () -> back.load(file));
        assertTrue(Files.exists(dir.resolve("data/colorednetherportals.json.broken")));
        assertEquals(0, back.size());
    }
}
