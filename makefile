JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
        $(JC) $(JFLAGS) src/*.java

CLASSES = \
        Loggers.java \
        packet.java \
        receiver.java \
        sender.java 

default: classes

classes: $(CLASSES:.java=.class)

clean:
        $(RM) *.class