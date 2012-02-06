package jlModel_extra;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface AnnotationNotPresentOnClassPath {
	int value() default 8;
}
