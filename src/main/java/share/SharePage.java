package share;

/**
 * Halaman unduh untuk tamu (HTML statis, CSS inline bergaya design system, tanpa JavaScript).
 * Tidak memuat input pengguna; token sudah divalidasi [A-Za-z0-9_-].
 */
final class SharePage {

    private SharePage() {
    }

    private static final String STYLE = """
            :root{--cream:#F7EFE2;--paper:#FFFBF3;--ink:#241B16;--ink-2:#6B5D52;--verm:#D9411E;--line:#E4D6C0}
            *{box-sizing:border-box;margin:0;padding:0}
            body{background:var(--cream);color:var(--ink);font-family:"Plus Jakarta Sans",system-ui,-apple-system,"Segoe UI",sans-serif;padding:28px 20px 48px}
            main{max-width:560px;margin:0 auto}
            .wm{font-family:Fraunces,Georgia,"Times New Roman",serif;font-weight:600;font-size:28px;letter-spacing:-.02em}
            .wm i{font-weight:400}.wm b{color:var(--verm);font-weight:600}
            h1{font-family:Fraunces,Georgia,"Times New Roman",serif;font-weight:560;font-size:44px;line-height:1;letter-spacing:-.03em;margin:28px 0 22px}
            h1 em{font-weight:400}
            .strip{display:block;max-width:100%;max-height:70vh;margin:0 auto;box-shadow:0 18px 40px rgba(36,27,22,.18)}
            .btn{display:flex;align-items:center;justify-content:center;min-height:64px;border-radius:999px;font-weight:700;font-size:20px;text-decoration:none;margin-top:22px}
            .primary{background:var(--verm);color:var(--paper)}
            .outline{border:3px solid var(--ink);color:var(--ink);min-height:56px;font-size:18px;margin-top:12px}
            h2{font-size:15px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:var(--ink-2);margin:36px 0 12px}
            .grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
            .card{background:var(--paper);border:2px solid var(--line);border-radius:22px;padding:10px}
            .card img{display:block;width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:14px}
            p{color:var(--ink-2);font-size:16px;line-height:1.5}
            """;

    private static String shell(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title><style>" + STYLE + "</style></head><body><main>"
                + "<div class=\"wm\">Van <i>de</i> B<b>oo</b>th</div>" + body + "</main></body></html>";
    }

    static String download(String token, int photos) {
        String base = "/s/" + token;
        StringBuilder b = new StringBuilder();
        b.append("<h1>Your strip <em>is ready.</em></h1>")
                .append("<img class=\"strip\" src=\"").append(base).append("/strip.png\" alt=\"Photo strip\">")
                .append("<a class=\"btn primary\" href=\"").append(base)
                .append("/strip.png\" download=\"van-de-booth-strip.png\">Download strip</a>");
        if (photos > 0) {
            b.append("<h2>Photos</h2><div class=\"grid\">");
            for (int i = 1; i <= photos; i++) {
                String src = base + "/photo/" + i + ".jpg";
                b.append("<div class=\"card\"><img src=\"").append(src).append("\" alt=\"Photo ").append(i).append("\">")
                        .append("<a class=\"btn outline\" href=\"").append(src)
                        .append("\" download=\"van-de-booth-photo-").append(i).append(".jpg\">Download photo ")
                        .append(i).append("</a></div>");
            }
            b.append("</div>");
        }
        b.append("<p style=\"margin-top:28px\">Photos stay on the booth computer. This link stops working after a few hours.</p>");
        return shell("Your photos - Van de Booth", b.toString());
    }

    static String testPage() {
        return shell("Sharing works - Van de Booth",
                "<h1>Sharing <em>works.</em></h1><p>Guests on this network can open their download links.</p>");
    }

    static String notFound() {
        return shell("Link not found - Van de Booth",
                "<h1>Link <em>not found.</em></h1><p>This link has expired or does not exist. Ask the host for a new code.</p>");
    }
}
