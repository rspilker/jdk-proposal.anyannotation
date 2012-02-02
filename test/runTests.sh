#!/bin/bash
CURDIR=`pwd`
cd `dirname $0`
PASSED=0
FAILED=0

if [ ! -f ../dist/anyannotation.jar ]; then
	echo "CANT RUN TESTS: First compile the live patcher by running 'ant dist' from the project's home directory."
	cd "$CURDIR"
	exit 2
fi

for f in reflection/*.java
do
	BASENAME=${f%.*}
	echo -n TESTING "$BASENAME"...
	javac -J-javaagent:../dist/anyannotation.jar "$f" &>"$BASENAME.compiler-result.actual"
	if [ $? == 0 ]; then
		if [ -f "$BASENAME.compiler-result.txt" ]; then
			echo " FAIL: Compiler should have produced an error but did not."
			echo "Expected error:"
			cat "$BASENAME.compiler-result.txt"
			FAILED=$[FAILED+1]
		else
			java -javaagent:../dist/anyannotation.jar -cp . reflection.`basename $BASENAME` &>"$BASENAME.result.actual"
			diff -qaiBwN "$BASENAME.result.actual" "$BASENAME.result.txt" >/dev/null
			if [ $? == 0 ]; then
				echo " OK"
				PASSED=$[PASSED+1]
			else
				echo " FAIL: Result of executing compiled class is not equal to expected result."
				echo "Expected result:"
				cat "$BASENAME.result.txt"
				echo "Actual result:"
				cat "$BASENAME.result.actual"
				FAILED=$[FAILED+1]
			fi
		fi
	else
		if [ ! -f "$BASENAME.compiler-result.txt" ]; then
			echo " FAIL: Compiler should have succeeded."
			echo "Actual error:"
			cat "$BASENAME.compiler-result.actual"
			FAILED=$[FAILED+1]
		else
			diff -qaiBwN "$BASENAME.compiler-result.actual" "$BASENAME.compiler-result.txt" >/dev/null
			if [ $? != 0 ]; then
				echo " FAIL: Compiler errors appropriately but error message is incorrect."
				echo "Expected error:"
				cat "$BASENAME.compiler-result.txt"
				echo "Actual error:"
				cat "$BASENAME.compiler-result.actual"
				FAILED=$[FAILED+1]
			else
				echo " OK"
				PASSED=$[PASSED+1]
			fi
		fi
	fi
done

javac -J-javaagent:../dist/anyannotation.jar jlModel/*.java
echo -n "TESTING javax.lang.model. Compiling..."
if [ $? != 0 ]; then
	echo " FAIL: Compilation of tests failed."
	FAILED=$[FAILED+1]
else
	echo " OK"
	PASSED=$[PASSED+1]
	for f in jlModel/Test*.java
	do
		BASENAME=${f%.*}
		echo -n TESTING "$BASENAME"...
		javac -J-javaagent:../dist/anyannotation.jar -cp . -processor jlModel.AnnotationProcessor "$f" &>"$BASENAME.compiler-result.actual"
		diff -qaiBwN "$BASENAME.compiler-result.actual" "$BASENAME.compiler-result.txt" >/dev/null
		if [ $? == 0 ]; then
			echo " OK"
			PASSED=$[PASSED+1]
		else
			echo " FAIL: Result of running annotation processor is not equal to expected result."
			echo "Expected output:"
			cat "$BASENAME.compiler-result.txt"
			echo "Actual output:"
			cat "$BASENAME.compiler-result.actual"
			FAILED=$[FAILED+1]
		fi
	done
fi

echo -n "TESTING Compile of new-form annotation with processors enabled..."
javac -J-javaagent:../dist/anyannotation.jar -cp . -AjlModel.silent -processor jlModel.AnnotationProcessor jlModel/*.java
if [ $? != 0 ]; then
	echo " FAIL: Compilation of new-form annotations with processors enabled failed."
	FAILED=$[FAILED+1]
else
	echo " OK"
	PASSED=$[PASSED+1]
fi

echo "tests passed: $PASSED"
if [ $FAILED != 0 ]; then
	echo "TESTS FAILED: $FAILED"
	cd "$CURDIR"
	exit 1
fi
cd "$CURDIR"
exit 0
