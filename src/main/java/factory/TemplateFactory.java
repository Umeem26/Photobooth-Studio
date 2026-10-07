package factory;

import model.StripTemplate;
import template.TemplateVertical;
import template.TemplateHorizontal;
import template.BrandedStripTemplate;
import template.StripLayout;

public class TemplateFactory {

    /**
     * Membuat strip bermerek Fase 2 (vertical-4, vertical-3, horizontal-3) dengan caption footer.
     * Mengembalikan null bila ID tidak dikenali.
     */
    public StripTemplate createTemplate(String templateId, String caption) {
        return StripLayout.byId(templateId)
                .<StripTemplate>map(layout -> new BrandedStripTemplate(layout, caption))
                .orElseGet(() -> createTemplate(templateId));
    }

    public StripTemplate createTemplate(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return null;
        }

        if (templateId.equalsIgnoreCase("TPL-V")) {
            return new TemplateVertical(4); // Default
        } else if (templateId.equalsIgnoreCase("TPL-V-2")) {
            return new TemplateVertical(2);
        } else if (templateId.equalsIgnoreCase("TPL-V-3")) {
            return new TemplateVertical(3);
        } else if (templateId.equalsIgnoreCase("TPL-V-4")) {
            return new TemplateVertical(4);

        } else if (templateId.equalsIgnoreCase("TPL-H")) {
            return new TemplateHorizontal(4); // Default
        } else if (templateId.equalsIgnoreCase("TPL-H-2")) {
            return new TemplateHorizontal(2);
        } else if (templateId.equalsIgnoreCase("TPL-H-3")) {
            return new TemplateHorizontal(3);
        } else if (templateId.equalsIgnoreCase("TPL-H-4")) {
            return new TemplateHorizontal(4);
        }

        return null;
    }
}