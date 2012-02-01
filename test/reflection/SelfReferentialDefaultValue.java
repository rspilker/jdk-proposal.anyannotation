package doc;
import java.lang.annotation.*;
import java.util.Arrays;

@SelfRef
public class SelfReferentialDefaultValue {
	public static void main(String[] args) {
		SelfRef ann = SelfReferentialDefaultValue.class.getAnnotation(SelfRef.class);
		System.out.println(ann.value());
	}
}

@Retention(RetentionPolicy.RUNTIME)
@interface SelfRef {
	Annotation value() default @SelfRef;
}
