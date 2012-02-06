#!/bin/bash
CURDIR=`pwd`
cd `dirname $0`

#JAVA7HOME=/path/to/jdk7/home

TPASSED=0
TFAILED=0
JAVA_BASE=""


function sanityCheck() {
	FUNFAIL=0
	if [ "$JAVA7HOME" != "" ]; then
		JAVA_BASE=$JAVA7HOME
	elif [ "$JAVA_HOME" != "" ]; then
		JAVA_BASE=$JAVA_HOME
	else
		echo "WARNING: Please define JAVA7HOME (or JAVA_HOME) before running the tests."
		FUNFAIL=1
	fi
	if [ ! -f ../dist/anyannotation.jar ]; then
		echo "CANT RUN TESTS: First compile the live patcher by running 'ant dist' from the project's home directory."
		cd "$CURDIR"
		exit 2
	fi
	
	"$JAVA_BASE/bin/javac" -version 2>&1 |grep -q 1.7
	
	if [ $? == 1 ]; then
		FUNFAIL=1
		echo "WARNING: Your javac's version is not 1.7 but " `$JAVA_BASE/bin/javac -version 2>&1` ' - the tests will probably fail!'
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
		"$JAVA_BASE/bin/javac" $1 "$f" &>"$BASENAME.compiler-result.actual"
		if [ $? == 0 ]; then
			if [ -f "$BASENAME.compiler-result.txt" ]; then
				echo " FAIL: Compiler should have produced an error but did not."
				echo "Expected error:"
				cat "$BASENAME.compiler-result.txt"
				FAILED=$[FAILED+1]
				TFAILED=$[TFAILED+1]
			else
				"$JAVA_BASE/bin/java" $2 -cp . reflection.`basename $BASENAME` &>"$BASENAME.result.actual"
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
	"$JAVA_BASE/bin/javac" $1 jlModel/*.java
	"$JAVA_BASE/bin/javac" $1 -cp . jlModel_extra/jlModel_extra/*.java
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
			"$JAVA_BASE/bin/javac" $1 -cp . -processor jlModel.AnnotationProcessor "$f" &>"$BASENAME.compiler-result.actual"
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
		
		echo -n "TESTING AnnotationNotOnClassPath..."
		BASENAME="jlModel_extra/jlModel_extra/TestAnnotationNotOnClassPath"
		"$JAVA_BASE/bin/javac" $1 -cp . -implicit:none -sourcepath jlModel_extra -processorpath . -processor jlModel.AnnotationProcessor $BASENAME.java &>"$BASENAME.compiler-result.actual"
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
	fi

	echo -n "TESTING Compile of new-form annotation with processors enabled..."
	"$JAVA_BASE/bin/javac" $1 -cp . -AjlModel.silent -processor jlModel.AnnotationProcessor jlModel/*.java
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

echo "TEST : java reflection : java7  : hg patch"
testReflection "-J-Xbootclasspath/p:../lib/build/javac7.jar -J-Xbootclasspath/p:../patchedJavac" "-javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1
echo "TEST : java reflection : java7  : live agent"
testReflection "-J-javaagent:../dist/anyannotation.jar" "-javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1

echo "TEST : java.lang.Model : javac7 : hg patch"
testJlModel "-J-Xbootclasspath/p:../lib/build/javac7.jar -J-Xbootclasspath/p:../patchedJavac" || SCRIPTFAIL=1
echo "TEST : java.lang.Model : javac7 : live agent"
testJlModel "-J-javaagent:../dist/anyannotation.jar" || SCRIPTFAIL=1

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
