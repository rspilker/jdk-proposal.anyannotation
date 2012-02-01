package reflection;
import java.lang.annotation.*;

@AnnWithAnnDefaultValue
public class AnnotationWithAnnotationDefaultValue {
	public static void main(String[] args) {
		AnnWithAnnDefaultValue ann = AnnotationWithAnnotationDefaultValue.class.getAnnotation(AnnWithAnnDefaultValue.class);
		System.out.println(ann.value());
	}
}

@Retention(RetentionPolicy.RUNTIME)
@interface AnnWithAnnDefaultValue {
	Annotation value() default @Deprecated;
}
