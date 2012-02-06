#!/bin/bash
CURDIR=`pwd`
cd `dirname $0`

JAVA6HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home
JAVA7HOME=/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home
#JAVA6HOME=.
#JAVA7HOME=.

TPASSED=0
TFAILED=0

function sanityCheck() {
	FUNFAIL=0
	if [ "$JAVA6HOME" = "" ]; then
		echo "WARNING: Please define JAVA6HOME at the top of this script file before running the tests."
		FUNFAIL=1
	fi
	if [ "$JAVA7HOME" = "" ]; then
		echo "WARNING: Please define JAVA7HOME at the top of this script file before running the tests."
		FUNFAIL=1
	fi
	if [ ! -f ../dist/anyannotation.jar ]; then
		echo "CANT RUN TESTS: First compile the live patcher by running 'ant dist' from the project's home directory."
		cd "$CURDIR"
		exit 2
	fi
	if [ $FUNFAIL -eq 0 ]; then
		true
	else
		false
	fi
}

function testReflection() {
	PASSED=0
	FAILED=0
	for f in reflection/*.java
	do
		BASENAME=${f%.*}
		echo -n TESTING "$BASENAME"...
		$1 "$f" &>"$BASENAME.compiler-result.actual"
		if [ $? == 0 ]; then
			if [ -f "$BASENAME.compiler-result.txt" ]; then
				echo " FAIL: Compiler should have produced an error but did not."
				echo "Expected error:"
				cat "$BASENAME.compiler-result.txt"
				FAILED=$[FAILED+1]
				TFAILED=$[TFAILED+1]
			else
				$2 -javaagent:../dist/anyannotation.jar -cp . reflection.`basename $BASENAME` &>"$BASENAME.result.actual"
				diff -qaiBwN "$BASENAME.result.actual" "$BASENAME.result.txt" >/dev/null
				if [ $? == 0 ]; then
					echo " OK"
					PASSED=$[PASSED+1]
					TPASSED=$[TPASSED+1]
				else
					echo " FAIL: Result of executing compiled class is not equal to expected result."
					echo "Expected result:"
					cat "$BASENAME.result.txt"
					echo "Actual result:"
					cat "$BASENAME.result.actual"
					FAILED=$[FAILED+1]
					TFAILED=$[TFAILED+1]
				fi
			fi
		else
			if [ ! -f "$BASENAME.compiler-result.txt" ]; then
				echo " FAIL: Compiler should have succeeded."
				echo "Actual error:"
				cat "$BASENAME.compiler-result.actual"
				FAILED=$[FAILED+1]
				TFAILED=$[TFAILED+1]
			else
				diff -qaiBwN "$BASENAME.compiler-result.actual" "$BASENAME.compiler-result.txt" >/dev/null
				if [ $? != 0 ]; then
					echo " FAIL: Compiler errors appropriately but error message is incorrect."
					echo "Expected error:"
					cat "$BASENAME.compiler-result.txt"
					echo "Actual error:"
					cat "$BASENAME.compiler-result.actual"
					FAILED=$[FAILED+1]
					TFAILED=$[TFAILED+1]
				else
					echo " OK"
					PASSED=$[PASSED+1]
					TPASSED=$[TPASSED+1]
				fi
			fi
		fi
	done
	
	echo "tests passed: $PASSED"
	if [ $FAILED != 0 ]; then
		echo "TESTS FAILED: $FAILED"
		false
	fi
	true
}

function testJlModel() {
	PASSED=0
	FAILED=0
	$1 jlModel/*.java
	echo -n "TESTING javax.lang.model. Compiling..."
	if [ $? != 0 ]; then
		echo " FAIL: Compilation of tests failed."
		FAILED=$[FAILED+1]
		TFAILED=$[TFAILED+1]
	else
		echo " OK"
		PASSED=$[PASSED+1]
		TPASSED=$[TPASSED+1]
		for f in jlModel/Test*.java
		do
			BASENAME=${f%.*}
			echo -n TESTING "$BASENAME"...
			$1 -cp . -processor jlModel.AnnotationProcessor "$f" &>"$BASENAME.compiler-result.actual"
			diff -qaiBwN "$BASENAME.compiler-result.actual" "$BASENAME.compiler-result.txt" >/dev/null
			if [ $? -eq 0 ]; then
				echo " OK"
				PASSED=$[PASSED+1]
				TPASSED=$[TPASSED+1]
			else
				echo " FAIL: Result of running annotation processor is not equal to expected result."
				echo "Expected output:"
				cat "$BASENAME.compiler-result.txt"
				echo "Actual output:"
				cat "$BASENAME.compiler-result.actual"
				FAILED=$[FAILED+1]
				TFAILED=$[TFAILED+1]
			fi
		done
	fi

	echo -n "TESTING Compile of new-form annotation with processors enabled..."
	$1 -cp . -AjlModel.silent -processor jlModel.AnnotationProcessor jlModel/*.java
	if [ $? != 0 ]; then
		echo " FAIL: Compilation of new-form annotations with processors enabled failed."
		FAILED=$[FAILED+1]
		TFAILED=$[TFAILED+1]
	else
		echo " OK"
		PASSED=$[PASSED+1]
		TPASSED=$[TPASSED+1]
	fi

	echo "tests passed: $PASSED"
	if [ $FAILED != 0 ]; then
		echo "TESTS FAILED: $FAILED"
		false
	fi
	true
}

SCRIPTFAIL=0

sanityCheck || SCRIPTFAIL=1

if [ "$JAVA7HOME" != "" ]; then
	echo "TEST : java reflection : java7  : hg patch"
	testReflection "$JAVA7HOME/bin/javac -J-Xbootclasspath/p:../patchedJavac:../lib/build/javac7.jar" "$JAVA7HOME/bin/java -javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
	echo "TEST : java reflection : java7  : live agent"
	testReflection "$JAVA7HOME/bin/javac -J-javaagent:../dist/anyannotation.jar" "$JAVA7HOME/bin/java -javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
fi
if [ "$JAVA6HOME" != "" ]; then
	echo "TEST : java reflection : java6  : live agent"
	testReflection "$JAVA6HOME/bin/javac -J-javaagent:../dist/anyannotation.jar" "$JAVA6HOME/bin/java -javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
fi

if [ "$JAVA7HOME" != "" ]; then
	echo "TEST : java.lang.Model : javac7 : hg patch"
	testJlModel "$JAVA7HOME/bin/javac -J-Xbootclasspath/p:../patchedJavac:../lib/build/javac7.jar" || SCRIPTFAIL=1
	echo "TEST : java.lang.Model : javac7 : live agent"
	testJlModel "$JAVA7HOME/bin/javac -J-javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
fi

if [ "$JAVA6HOME" != "" ]; then
	echo "TEST : java.lang.Model : javac6 : live agent"
	testJlModel "$JAVA6HOME/bin/javac -J-javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
fi

echo "total passed: $TPASSED"
if [ $TFAILED -ne 0 ]; then
	echo "TOTAL FAILED: $TFAILED"
fi

cd "$CURDIR"
if [ $SCRIPTFAIL -eq 0 ]; then
	exit 0
else
	exit 1
fi
