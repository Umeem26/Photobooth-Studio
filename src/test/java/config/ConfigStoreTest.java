package config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

class ConfigStoreTest {

    @TempDir
    Path tmp;

    private ConfigStore store() {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.resolve("out").toString());
        return ConfigStore.of(p, tmp.resolve("cfg"));
    }

    @Test
    void phaseThreeDefaults() {
        AppConfig c = store().current();
        assertFalse(c.paymentEnabled());
        assertEquals(25000, c.paymentPrice());
        assertTrue(c.shareEnabled());
        assertEquals(8080, c.sharePort());
        assertEquals(6, c.shareExpiryHours());
        assertEquals(2, c.printMaxCopies());
        assertFalse(c.printTwoUp());
        assertFalse(c.printToFile());
        assertEquals(3, c.countdownSeconds());
        assertEquals(1, c.pauseSeconds());
        assertEquals(c.outputDir().resolve("print-queue"), c.printQueueDir());
    }

    @Test
    void mirrorPhotosDefaultsToOnAndIsEditableAndValidated() throws Exception {
        ConfigStore store = store();
        assertTrue(store.current().mirrorPhotos());

        AppConfig off = store.update(Map.of(AppConfig.PHOTOS_MIRROR_KEY, "false"));
        assertFalse(off.mirrorPhotos());
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(tmp.resolve("cfg").resolve("config.properties"))) {
            saved.load(in);
        }
        assertEquals("false", saved.getProperty(AppConfig.PHOTOS_MIRROR_KEY));
        assertTrue(store.update(Map.of(AppConfig.PHOTOS_MIRROR_KEY, "true")).mirrorPhotos());

        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.PHOTOS_MIRROR_KEY, "maybe")));
        assertTrue(store.current().mirrorPhotos(), "nilai salah tidak mengubah apa pun");
        assertTrue(AppConfig.EDITABLE_KEYS.contains(AppConfig.PHOTOS_MIRROR_KEY));
    }

    @Test
    void layoutsOfferedDefaultsToAllSixAndPersistsValidatedSelection() throws Exception {
        ConfigStore store = store();
        assertEquals(List.of("vertical-4", "vertical-3", "horizontal-3", "postcard-1", "grid-4", "grid-6"),
                store.current().layoutsOffered());

        // urutan mengikuti definisi layout, bukan urutan input
        AppConfig next = store.update(Map.of(AppConfig.LAYOUTS_OFFERED_KEY, " grid-6 , vertical-4 "));
        assertEquals(List.of("vertical-4", "grid-6"), next.layoutsOffered());
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(tmp.resolve("cfg").resolve("config.properties"))) {
            saved.load(in);
        }
        assertEquals("vertical-4,grid-6", saved.getProperty(AppConfig.LAYOUTS_OFFERED_KEY));

        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.LAYOUTS_OFFERED_KEY, ",")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.LAYOUTS_OFFERED_KEY, "vertical-4,wide-9")));
        assertEquals(List.of("vertical-4", "grid-6"), store.current().layoutsOffered(), "gagal validasi tidak mengubah apa pun");
    }

    @Test
    void updateAppliesImmediatelyNotifiesAndPersistsAtomically() throws Exception {
        ConfigStore store = store();
        List<AppConfig> seen = new ArrayList<>();
        store.addListener(seen::add);

        AppConfig next = store.update(Map.of(AppConfig.EVENT_NAME_KEY, "Rina & Bayu", AppConfig.PAYMENT_ENABLED_KEY, "true"));

        assertEquals("Rina & Bayu", next.eventName());
        assertTrue(store.current().paymentEnabled());
        assertEquals(1, seen.size());
        Path file = tmp.resolve("cfg").resolve("config.properties");
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            saved.load(in);
        }
        assertEquals("Rina & Bayu", saved.getProperty("event.name"));
        assertEquals("true", saved.getProperty("payment.enabled"));
        assertNull(saved.getProperty("output.dir"), "hanya kunci yang diubah yang ditulis");
        assertFalse(Files.exists(tmp.resolve("cfg").resolve("config.properties.tmp")));
    }

    @Test
    void invalidValuesAreRejectedAndConfigUnchanged() throws Exception {
        ConfigStore store = store();
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.COUNTDOWN_SECONDS_KEY, "9")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.SHARE_EXPIRY_KEY, "12")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.PRINT_LAYOUT_KEY, "three-up")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.PAYMENT_ENABLED_KEY, "ya")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.SHARE_HOST_KEY, "a b/../c")));
        assertThrows(IllegalArgumentException.class, () -> store.update(Map.of(AppConfig.OUTPUT_DIR_KEY, "C:/")),
                "output.dir tidak boleh diubah operator");
        assertEquals(3, store.current().countdownSeconds());
        assertFalse(Files.exists(tmp.resolve("cfg").resolve("config.properties")));
    }

    @Test
    void persistedFileIsLoadedOnNextStart() throws Exception {
        store().update(Map.of(AppConfig.MAX_RETAKES_KEY, "4"));
        String previous = System.getProperty(AppConfig.CONFIG_DIR_SYSTEM_PROPERTY);
        System.setProperty(AppConfig.CONFIG_DIR_SYSTEM_PROPERTY, tmp.resolve("cfg").toString());
        try {
            assertEquals(4, ConfigStore.load().current().maxRetakes());
            assertEquals(tmp.resolve("cfg").toAbsolutePath().normalize(), ConfigStore.defaultConfigDir());
        } finally {
            if (previous == null) System.clearProperty(AppConfig.CONFIG_DIR_SYSTEM_PROPERTY);
            else System.setProperty(AppConfig.CONFIG_DIR_SYSTEM_PROPERTY, previous);
        }
    }
}
