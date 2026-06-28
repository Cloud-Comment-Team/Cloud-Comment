package com.cloudcomment.site.api.validation;

import com.cloudcomment.site.domain.SiteInputRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class HttpOriginValidator implements ConstraintValidator<ValidHttpOrigin, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SiteInputRules.normalizeOrigin(value).isPresent();
    }
}
