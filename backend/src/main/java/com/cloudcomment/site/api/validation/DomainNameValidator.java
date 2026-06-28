package com.cloudcomment.site.api.validation;

import com.cloudcomment.site.domain.SiteInputRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class DomainNameValidator implements ConstraintValidator<ValidDomainName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SiteInputRules.normalizeDomain(value).isPresent();
    }
}
