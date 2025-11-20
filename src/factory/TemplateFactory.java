package factory;

import model.StripTemplate;
import template.TemplateSquare;
import template.TemplateVertical;
import template.TemplateClassicStrip;
import template.TemplateHorizontal;
import template.TemplateFeatured;
import template.TemplateFourCorners;

public class TemplateFactory {

    public StripTemplate createTemplate(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return null;
        }

        if (templateId.equalsIgnoreCase("TPL-V")) {
            return new TemplateVertical();
        } else if (templateId.equalsIgnoreCase("TPL-SQ")) {
            return new TemplateSquare();
        } else if (templateId.equalsIgnoreCase("TPL-C")) {
            return new TemplateClassicStrip();

        } else if (templateId.equalsIgnoreCase("TPL-H")) {
            return new TemplateHorizontal();
        } else if (templateId.equalsIgnoreCase("TPL-F")) {
            return new TemplateFeatured();
        } else if (templateId.equalsIgnoreCase("TPL-COR")) {
            return new TemplateFourCorners();
        }

        return null;
    }
}