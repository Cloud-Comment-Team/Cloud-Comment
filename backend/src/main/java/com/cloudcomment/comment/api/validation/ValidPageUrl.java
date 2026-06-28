package com.cloudcomment.comment.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
    ElementType.FIELD,
    ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PageUrlValidator.class)
public @interface ValidPageUrl {

    String message() default "must be an absolute http(s) URL without user info or fragment";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
