package jlModel;

import java.lang.annotation.Annotation;

public @interface SubAnnotation {
	Annotation ann();
	int test() default 5;
}
