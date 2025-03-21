# mvn clean
mvn clean package
# execute javassit in webserver with a focus on raytracer~
cd webserver
java --add-opens java.base/java.lang=ALL-UNNAMED -cp target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:../javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.raytracer,pt.ulisboa.tecnico.cnv.imageproc:output pt.ulisboa.tecnico.cnv.webserver.WebServer
# java --add-opens java.base/java.lang=ALL-UNNAMED -cp /home/ec2-user/cnv24-g06/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:/home/ec2-user/cnv24-g06/javassist/target/JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.raytracer,pt.ulisboa.tecnico.cnv.imageproc:output pt.ulisboa.tecnico.cnv.webserver.WebServer
