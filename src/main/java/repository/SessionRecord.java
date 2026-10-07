package repository;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data satu sesi foto yang akan diarsipkan.
 *
 * @param templateId ID template yang dipakai
 * @param frames     foto per slot (sudah difilter), urut sesuai slot
 * @param strip      hasil komposisi strip
 * @param video      video strip (boleh null)
 */
public record SessionRecord(String templateId, List<BufferedImage> frames, BufferedImage strip, Path video) {

    public SessionRecord {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId wajib diisi");
        }
        if (strip == null) {
            throw new IllegalArgumentException("strip wajib diisi");
        }
        // List.copyOf menolak elemen null, jadi pakai salinan biasa yang tetap read-only
        frames = frames == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(frames));
    }
}
