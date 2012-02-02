package jlModel;

import java.lang.annotation.Annotation;

public @interface NewAnnotation {
	Annotation[] value() default { @Override, @Deprecated, @SubAnnotation(test = 10, ann = @SuppressWarnings("all")) };
}
