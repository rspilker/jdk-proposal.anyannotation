package reflection;
import java.lang.annotation.*;
import java.util.Arrays;

@AnnWithDefaultValue
public class AnnotationWithArrayAnnotationDefaultValue {
	public static void main(String[] args) {
		AnnWithDefaultValue ann = AnnotationWithArrayAnnotationDefaultValue.class.getAnnotation(AnnWithDefaultValue.class);
		System.out.println(Arrays.asList(ann.value()));
	}
}

@Retention(RetentionPolicy.RUNTIME)
@interface AnnWithDefaultValue {
	Annotation[] value() default {@Override, @Deprecated};
}
