package com.cloudcomment.comment.api.validation;

import com.cloudcomment.comment.domain.PageUrlRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class PageUrlValidator implements ConstraintValidator<ValidPageUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PageUrlRules.normalize(value).isPresent();
    }
}
