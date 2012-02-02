package jlModel;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions("jlModel.silent")
public class AnnotationProcessor extends AbstractProcessor {
	private boolean beSilent = false;
	@Override public synchronized void init(ProcessingEnvironment processingEnv) {
		beSilent = processingEnv.getOptions().containsKey("jlModel.silent");
	}
	
	@Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (beSilent) return true;
		for (TypeElement elem : annotations) {
			if (!elem.getQualifiedName().contentEquals("jlModel.NewAnnotation")) continue;
			for (Element member : elem.getEnclosedElements()) {
				ExecutableElement exe = (ExecutableElement) member;
				System.out.println(exe.getReturnType());
				System.out.println(exe.getDefaultValue());
			}
		}
		for (Element e : roundEnv.getRootElements()) {
			NewAnnotation na = e.getAnnotation(NewAnnotation.class);
			if (na != null) System.out.println(na);
			SubAnnotation sa = e.getAnnotation(SubAnnotation.class);
			if (sa != null) System.out.println(sa);
		}
		return true;
	}
}
