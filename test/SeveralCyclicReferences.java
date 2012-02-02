// Expected: 4 errors
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface SelfRef {
	// Should error on @SelfRef
	Annotation value() default @SelfRef;
}

@Retention(RetentionPolicy.CLASS)
@interface WrappedSelfRef {
	// Should error on @WrappedSelfRef
	Annotation value() default @Wrapper(@WrappedSelfRef);
}

@Retention(RetentionPolicy.RUNTIME)
@interface Wrapper {
	Annotation value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Ping {
	// Should error on @Pong
	Annotation[] value() default {@Pang, @Pong};
}

@Retention(RetentionPolicy.RUNTIME)
@interface Pong {
	Annotation[] value() default {@Deprecated, @Ping};
}

@Retention(RetentionPolicy.RUNTIME)
@interface Pang {
	Annotation value() default @Deprecated;
}

@Retention(RetentionPolicy.RUNTIME)
@interface SimplePing {
	// Should error on @SimplePong
	SimplePong value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface SimplePong {
	SimplePing value();
}