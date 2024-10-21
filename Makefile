.PHONY: build clean

build:
	javac MyBot.java

run:
	java MyBot

clean:
	rm -f *.class
