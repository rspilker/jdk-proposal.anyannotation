#!/bin/bash
CURDIR=`pwd`
cd `dirname $0`
PASSED=0
FAILED=0
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
			diff -qaiBwN --strip-trailing-cr "$BASENAME.result.actual" "$BASENAME.result.txt" >/dev/null
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
			diff -qaiBwN --strip-trailing-cr "$BASENAME.compiler-result.actual" "$BASENAME.compiler-result.txt" >/dev/null
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
echo "tests passed: $PASSED"
if [ $FAILED != 0 ]; then
	echo "TESTS FAILED: $FAILED"
	cd "$CURDIR"
	exit 1
fi
cd "$CURDIR"
exit 0
