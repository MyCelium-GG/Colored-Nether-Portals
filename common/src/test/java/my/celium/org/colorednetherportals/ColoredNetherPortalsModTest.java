package my.celium.org.colorednetherportals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import my.celium.org.Mycel;
import my.celium.org.testing.MycelTesting;

/** The mod boots on Mycel with a small, well-defaulted config and both packet definitions. */
class ColoredNetherPortalsModTest {
    @BeforeAll
    static void initOnce(@TempDir Path tempDir) {
        MycelTesting.useConfigDir(tempDir);
        ColoredNetherPortalsMod.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(ColoredNetherPortalsMod.META));
        assertEquals("colorednetherportals", ColoredNetherPortalsMod.META.id());
    }

    @Test
    void configDefaults() {
        assertTrue(ColoredNetherPortalsMod.enabled.get());
        assertTrue(ColoredNetherPortalsMod.allowAnimated.get());
        assertTrue(ColoredNetherPortalsMod.dyeColoring.get());
        assertTrue(ColoredNetherPortalsMod.consumeDye.get());
        assertFalse(ColoredNetherPortalsMod.debug.get());
    }

    @Test
    void packetsAreNamespaced() {
        assertNotNull(ColoredNetherPortalsMod.setPortal);
        assertNotNull(ColoredNetherPortalsMod.portals);
        assertEquals("colorednetherportals", ColoredNetherPortalsMod.setPortal.id().getNamespace());
        assertEquals("colorednetherportals", ColoredNetherPortalsMod.portals.id().getNamespace());
    }

}
