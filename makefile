###################################################
# Targets:
###################################################
	
jc javac c compile: 
	javac src/receiver/*.java
	javac src/sender/*.java
	javac src/utility/*.java

###################################################
# Housekeeping:
###################################################

clean:
	-rm -f src/receiver/*.class *~
	-rm -f src/sender/*.class *~
	-rm -f src/utility/*.class *~

###################################################
# End
###################################################