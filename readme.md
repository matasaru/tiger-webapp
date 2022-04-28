LinkTiger meets Clojure
=======================

Minimal repo that shows the first steps taken to migrate LinkTiger's customer facing webapp - an embedded Jetty JSP application - to Clojure.   

Checkout any of the commits and:
* run the following command from within the project dir to build the jar
```bash 
mvn clean package
```

* run the following commands to start the webapp 
```bash
cd target
java -jar tiger-webapp.jar  
```

* open [http://localhost:8080](http://localhost:8080) in your browser

* press ctrl+C to stop the webapp

IntelliJ  will create a Run Configuration from .run/tiger-webapp.run.xml which you can use to run/debug the app.
