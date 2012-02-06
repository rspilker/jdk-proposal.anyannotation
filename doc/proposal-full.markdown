# The AnyAnnotation feature

Currently, the JLS specifies that only a direct subtype of `java.lang.annotation.Annotation`, defined using the `public @interface` syntax, is actually an annotation type, and that the return type of any member of an annotation declaration can only be an annotation type (or a primitive, String, Class, or a 1-dimensional array). It is therefore not possible in java 1.7 to create a member of an annotation declaration that implies 'any annotation is legal here'.

This proposal addresses the lack of this feature. It also moves java closer towards supporting hierarchical annotations (the ability to have one annotation declaration extend something other than `java.lang.annotation.Annotation` itself). In a nutshell, the proposed changes:

* Allow `java.lang.annotation.Annotation` as a valid return type for annotation declaration members (in JDK 1.7 this is not legal). Using this class as return type means any annotation is intended to be legal.
* Update the JLS to reflect this change (no changes to javadoc, reflection core API, and JVM specification needed).
* Patch an assert statement in the reflection core API, and 2 lines in javac, to support the spec.
* Add a new loop detection scheme to avoid an endless loop in the `default` construction of an annotation declaration member.
* Expand on the impact this change will have, both in regards to which tooling options now become possible and what impact this change can have on existing tools.

## Authors

* Reinier Zwitserloot (r.zwitserloot@projectlombok.org)
* Roel Spilker (r.spilker@projectlombok.org)
* Sander Koning (s.koning@projectlombok.org)
* Robbert Jan Grootjans (r.grootjans@projectlombok.org)

## Try it right now!

A special try-it-out javac agent is available here:

[http://projectlombok.org/anyannotation/anyannotation.jar]()

which can be used to try this feature out. Take any recent javac and invoke it as usual, but add a parameter, like so:

	javac -J-javaagent:anyannotation.jar *.java
	
For that execution run only, javac will include the suggested patches in this document. No patch is required to actually run the code produced by such a javac, unless you use the `-esa` option. In that case, the same agent will also fix `-esa`:

	java -esa -javagent:anyannotation.jar com.package.MyClass

This 'patcher' does not actually modify any of your system's files, it only patches the appropriate classes in memory.

## Changes required in the JDK and associated documentation

### JVM Specification: Zero changes required

_Based on [http://docs.oracle.com/javase/7/specs/jvms/JVMS-JavaSE7.pdf]()_

1. For annotations themselves: Section 4.7.16 - **The RuntimeVisibleAnnotations attribute** through Section 4.7.19 - **The RuntimeInvisibleParameterAnnotations attribute** specify how annotations should be encoded in the class file. These sections say the only way to store an annotation inside an annotation is as a new annotation block, and such a block _includes_ the type of the annotation. Thus, in `@Foo(@Bar)`, there is always a pointer to the constant pool entry with `com.package.Bar`. It is therefore not necessary to rely on the signatures available in the `Foo` type to understand its parameter, and therefore no change is necessary to the class file format to support the case where `@Foo`'s parameter is of type `Annotation`.

2. For annotation type declarations, the JVMS basically contains no specifications. They are just plain interfaces with a bit set (`ACC_ANNOTATION` - see section 4.1 `access_flags`). The only new aspect (other than the `ACC_ANNOTATION` bit, which is not affected by this proposal) is the classfile encoding of the `default` value feature of an annotation declaration member, which is described in section 4.7.20 - **The AnnotationDefault attribute**. The format of this attribute is explained in terms of section 4.7.16's annotation structure, which already supports this proposal without any changes required. There is absolutely no mention that the value of an _AnnotationDefault_ block has to match the type of the `method_info`'s return type (that aspect of the annotation spec is covered in the JLS only). In other words, the JVMS doesn't actually consider `int foo() default "Hello, World!";` illegal, though javac obviously would refuse to emit a class file if you tried it. This means that `Annotation onMethod() default @Deprecated;` isn't treated as illegal by the JVMS either, and thus the JVMS needs no updates to reflect that this would be a legal construct now. There is furthermore no commentary about the fact that the return type of an element of an `ACC_ANNOTATION` type can only be a primitive, String, Class, an annotation type, or a 1-dimensional array of those. Therefore, no comment needs to be added to explain that `java.lang.annotation.Annotation` is also legal even though it is not an annotation type according to the JLS.

### JLS Specification: A few changes required

_based on [http://docs.oracle.com/javase/7/specs/jls/JLS-JavaSE7.pdf]()_

1. 9.6 - **Annotation Types** This introductory section describes the actual declaration of an annotation type; no changes needed.

2. 9.6.1 - **Annotation Type Elements** requires two changes:

	_old:_

	It is a compile-time error if the return type of a method declared in an annotation
	type is not one of the following: a primitive type, String, Class, any parameterized
	invocation of Class, an enum type (S8.9), an annotation type, or an array type
	(S10) whose element type is one of the preceding types.

	_new:_
	
	It is a compile-time error if the return type of a method declared in an annotation
	type is not one of the following: a primitive type, String, Class, any parameterized
	invocation of Class, an enum type (S8.9), an annotation type, `java.lang.annotation.Annotation`, 
	or an array type (S10) whose element type is one of the preceding types.
	
	[smaller]  
	If the return type of an annotation method is declared to be
	`java.lang.annotation.Annotation` (or its array), any annotation (S9.7) is a valid value:
	
		@interface Getter {
			Annotation[] onMethod() default {@NonNull};
		}
	[/smaller]
	
	_old:_
	
	It is a compile-time error if an annotation type declaration T contains an element
	of type T, either directly or indirectly.
	
	[smaller]  
	For example, this is illegal:
	
		@interface SelfRef { SelfRef value(); }
		
	and so is this:
	
		@interface Ping { Pong value(); }
		@interface Pong { Ping value(); }
	[/smaller]
	
	_new (optional change; add an example to clarify):_
	
	[smaller]  
	For example, this is illegal:
	
		@interface SelfRef { SelfRef value(); }
		
	and so is this:
	
		@interface Ping { Pong value(); }
		@interface Pong { Ping value(); }
		
	as is this:
	
		@interface SelfRef { Annotation value() default @SelfRef; }
	[/smaller]

3. 9.6.2 - **Defaults for Annotation Type Elements** requires no changes. The relevant paragraph reads:

	It is a compile-time error if the type of the element is not commensurate (S9.7) with
the default value specified.

	After applying the required changes to sections S9.6.1 and S9.7.1, this statement is still valid.

4. 9.6.3 - **Predefined Annotation Types** requires no changes.

5. 9.7 - **Annotations** requires no changes (this is an introduction section).

6. 9.7.1 - **Normal Annotations** requires one change and one optional change.

	The production rule for _ElementValue_ does not need changing because it already mentions it can consist of an _Annotation_.
	
	_old_:
	
	The return type of this method defines the element type of the element-value pair.
	
	_new_:
	
	The return type of this method defines the element type of the element-value pair.
	If the return type is `java.lang.annotation.Annotation`, any annotation is a valid
	value.
	
	_old_:
	
	The type of V is assignment compatible (S5.2) with T, and furthermore:
	* If T is a primitive type or String, and V is a constant expression (S15.28).
	* V is not null.
	* If T is Class, or an invocation of Class, and V is a class literal (S15.8.2).
	* If T is an enum type, and V is an enum constant.
	
	_append an item to the list as clarification:_
	
	* If T is `java.lang.annotation.Annotation`, and V is an annotation.

7. 9.7.2 - **Marker Annotations** only describes a shorthand notation; needs no changes.

8. 9.7.3 - **Single-Element Annotations** only describes a shorthand notation; needs no changes.

9. 13.5.7 - **Evolution of Annotation Types** needs no changes.

### Changes to javadoc of existing java.* API: No changes required

1. method `java.lang.reflect.Method.getDefaultValue()`'s javadoc does not mention anything about annotation member's return types being restricted to a subset of legal types, therefore no update to include `java.lang.annotation.Annotation` in this subset is required.

2. `java.lang.Class.isAnnotation()` (and `getModifiers() & java.lang.reflect.Modifier.ANNOTATION`) is the only aspect of `Class` which is different / relevant for annotation types, and it also makes no mention of the return type limitations.

3. Existing annotations in the java base library itself (`@Override`, `@Deprecated`, etc - listed in JLS sections 9.6.3.1-9.6.3.7) do not have any methods which are an annotation type, nor do any of these types seem like they could use one. No updates are required or suggested for any of them.

4. The javadoc on `java.lang.annotation.Annotation` itself remains valid. It might be prudent to expand it with a section that explains that it can be used as a return type for an annotation method, but the other legal return types for annotation declaration members don't have this either. Therefore, for consistency's sake, this proposal does not include a change to this javadoc.

### Changes / additions to any of the method signatures of the reflection API or any other part of java base: No changes required

* `java.lang.reflect.Method.getDefaultValue()` already returns `java.lang.Object` and thus needs no changes.

* No new API is required to reflectively determine that a given annotation declaration member's return type is `Annotation`, because the way this return type is reflected is via a `java.lang.Class` return type, which is already capable of conveying `Annotation` as a value. This part is one of the few ways existing tools might break, as they may erroneously assume this return value can only be `java.lang.Class.class`, `java.lang.String.class`, any of the primitive wrappers, or a type which is an annotation type. This method could now also return `java.lang.annotation.Annotation` which is not itself an annotation type.

* No new API is required to reflectively read out annotation values, as these will still be specific instances of annotations.

* No new API is required to reflectively read out annotation defaults, as these, if present, will still be specific instances of annotations.

### Changes / additions to JVM library reflection core (java.*): No changes needed

1. `java.lang.reflect.Method.getDefaultValue()` delegates work to internal implementations and does not contain any code that would cause issues with a `java.lang.annotation.Annotation` return type. It does first acquire the return type of the method and then asks for an instance of the value that 'fits' this return type, but it leaves all checking to the internal implementations that provide both of these values (both the return type and an instance for the value given the byte array containing the raw `annotationDefault` data). The work is deferred to `sun.reflect.annotation.AnnotationParser` and `sun.reflection.annotation.AnnotationType`, which do need patches (see below).

2. `java.lang.reflect.Method.getAnnotation(java.lang.Class annotationClass)` defers work to `sun.reflect.annotation.AnnotationParser` and `sun.reflect.annotation.AnnotationType` as well. Same for `java.lang.reflect.Field.getAnnotation`, and `java.lang.Class.getAnnotation`. `java.lang.Package.getAnnotation` is a wrapper around a dummy `java.lang.Class` instance that holds annotation data (and thus, it too defers the work). These internal helpers do need patches (see below).

### Changes / additions to internal support classes used by reflective core: One tiny change needed to a system assertion

1. `sun.reflect.annotation.AnnotationType` - no changes necessary.

2. `sun.reflect.annotation.AnnotationInvocationHandler` - no changes necessary. In particular, the `equals()` implementation does not use the return type, only member values; all special handling defaults to `equals()`, `toString()`, `hashCode()` etc of the member value if the member value is an annotation type. annotation instances already have working `equals`, `hashCode`, `toString`, etc implementations, therefore no changes are necessary.

3. `sun.reflect.annotation.AnnotationParser` - Minor changes necessary.

_based on [http://hg.openjdk.java.net/jdk7/jdk7/jdk/file/9b8c96f96a0f/src/share/classes/sun/reflect/annotation/AnnotationParser.java]()_

* method `parseMemberValue` does NOT provide the expected type (parameter `memberType`) to the `parseAnnotation` helper method. Therefore, the fact that expected type is a previously invalid value (`java.lang.annotation.Annotation`) does not have any effect on parsing the annotation in the class file data.

* method `parseSig` does not check if the provided type is an annotation type. (It really can't, as the type is not guaranteed to be on the classpath, and therefore it has no way of knowing if the provided type is actually an annotation type. Nevertheless, the code does indeed includes no such check).

* method 'parseArray' (helper of `parseMemberValue`) contains an assertion (which can be enabled with the `-esa` javac option) on line 485 which needs updating:

		- assert componentType.isAnnotation();
		+ assert componentType.isAnnotation() || componentType == java.lang.annotation.Annotation.class;

### Changes to javac: Some changes needed

* javac checks that an annotation declaration member's return type is one of the allowed types. This check needs to be extended to consider `java.lang.annotation.Annotation` a legal return type value as well. No change in the way javac builds the class file to represent the annotation declaration is required:

In com.sun.tools.javac.comp.Check:2267
_based on [http://hg.openjdk.java.net/jdk7/jdk7/langtools/file/ce654f4ecfd8/src/share/classes/com/sun/tools/javac/comp/Check.java]()_

		- if ((type.tsym.flags() & Flags.ANNOTATION) != 0) {
		+ if ((type.tsym.flags() & Flags.ANNOTATION) != 0 ||  || types.isSameType(type, syms.annotationType)) {

* Loop detector [TODO]

* The error message with key 'cyclic.annotation.element' doesn't need changing.

* javac checks that an annotation's parameter is type compatible with the annotation's declaration. It does this using an 'assignment compatible' check, which will work fine with `java.lang.annotation.Annotation` as return type of the annotation declaration member method. However, this check is entered using an `if` statement which needs to be expanded:

In com.sun.tools.javac.comp.Annotate:224
_based on [http://hg.openjdk.java.net/jdk7/jdk7/langtools/file/ce654f4ecfd8/src/share/classes/com/sun/tools/javac/comp/Annotate.java]()_

		- if ((expected.tsym.flags() & Flags.ANNOTATION) != 0) {
		+ if ((expected.tsym.flags() & Flags.ANNOTATION) != 0 || types.isSameType(expected, syms.annotationType)) {

* No other changes are required. The error message with key `invalid.annotation.member.type` may need to be expanded to explain that `Annotation` is also a legal type.

## Source and binary compatibility

This proposal introduces no new or changed behaviour for any source code which is legal today, and no changes to the class file format. Therefore, existing source files which compile on javac 1.7 are not affected.

Existing tools also notice no changes for existing source and class files. However, newly compiled annotation declarations may now start using `java.lang.annotation.Annotation` as return type for members, and some existing tools may have assumed the legal set of return values couldn't change. These tools will need to be updated.

Technically, it is possible for existing annotations to be 'widened' - any return types which used to be a specific annotation type can be generalized to `java.lang.annotation.Annotation`, but this might cause problems with consumers of this annotation. The same problem occurs for any library update where signatures are changed, however.

## Use cases for this feature

One obvious use case for annotations is generating code. If this feature is added to the JDK, its possible to specify a list of annotations that should be put in the generated code. For example, an annotation that will generate a POJO with implementations for `equals`, `hashCode`, etcetera:

	@GeneratePOJO
	@AddAnnotations(onClass=@SuppressWarnings("all"))
	public class StudentTemplate {
		@AddAnnotations(onGetter={@NonNull, @javax.persistence.Id})
		private int unid;
	}

This feature also takes a step towards allowing hierarchical annotation definitions (where an annotation extends another annotation type, instead of `java.lang.annotation.Annotation`).

## Known issues

With the following construct:

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Foo {
		Annotation value() default @Foo;
	}
	
	@Foo class Test {
		public static void main(String[] args) {
			Foo x = Test.class.getAnnotation(Foo.class);
			x.value(); //stack overflow
		}
	}

The indicated line causes an endless loop when 'Test' is executed (no issues when compiling this code) which eventually terminates once the stack overflows, because the reflection core will continue to synthesize new proxy instances of `Foo` with a default value, which never ends. Prior to this proposal, it couldn't happen because the spec explicitly disallows cycles in the return types of any members of an annotation declaration. (Section 9.6.1).

We're working on including a patch to fix this bug.

The fix is to specify that the default value also may not contain such loops, and to add a check for such a loop in javac.

This alternate example of a loop does not cause any issues, and we propose that the spec does not disallow this usage:

	public @interface Foo {
		Annotation value() default @Override;
	}
	
	@Foo(@Foo(@Foo(@Foo()))) //acceptable
	class Test {}

However, we recognize that this may warrant a closer look.
