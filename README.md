# ingenico-devopsteam-test

## How to process a yaml input file structured this way :
```yaml
checks:
     ping:
          google: google.com:443
          mysite: mytest.test:443
```

to perform TCP connectivity checks and print the results :
OK: google
BAD: mysite


## in java

the project is build using maven 
The single test runs against the test input file

From the java directory :

To build the jar :

maven package

To run manually using the jar    :
java -cp target/ingenico-test-1.0-SNAPSHOT.jar fr.htz.ingenico.Main src/test/resources/input.yml

or simply :
java -jar target/ingenico-test-1.0-SNAPSHOT.jar  src/test/resources/input.yml
          

## in shell


## in python

