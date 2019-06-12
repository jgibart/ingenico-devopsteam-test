# ingenico-devopsteam-test

## How to process a yaml input file structured this way :
```yaml
checks:
     ping:
          google: google.com:443
          mysite: mytest.test:443
```

to perform TCP connectivity checks and print the results :
```
OK: google
BAD: mysite
```

## in java

the project is build using maven to build ( and has a single dependency, managed by maven, to parse yaml : snakeyaml )
The single test runs against the test input file

From the java directory :

To build the jar :

maven package

To run manually using the jar    :
java -cp target/ingenico-test-1.0-SNAPSHOT.jar fr.htz.ingenico.Main src/test/resources/input.yml

or simply :
java -jar target/ingenico-test-1.0-SNAPSHOT.jar  src/test/resources/input.yml
          

## in shell

From the bash directory :

bash connectivityTest.sh  ../java/src/test/resources/input.yml



## in python

From the python directory :

python2 connectivityTest.py  ../java/src/test/resources/input.yml
or
python3 connectivityTest.py  ../java/src/test/resources/input.yml


## conclusion
Code works in any language, but shell is very slow and bad at parsing data.
Python is very short and runs reasonably fast.
Java works fastest but is quite verbose. And java lacks native yaml support (and also lacks native json support by the way)