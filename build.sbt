
name := "toscience-api"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  ws,javaWs,javaJdbc,
  "org.marc4j" % "marc4j" % "2.8.0", 
  "junit" % "junit" % "4.11", 
  "org.apache.pdfbox" % "pdfbox" % "1.8.0",
  "org.apache.commons" % "commons-lang3" % "3.4",
  "org.bouncycastle" % "bcprov-jdk15" % "1.44",
  "org.bouncycastle" % "bcmail-jdk15" % "1.44", 
  "com.ibm.icu" % "icu4j" % "3.8",
  "com.itextpdf" % "itextpdf" % "5.4.1", 
  "org.antlr" % "antlr4" % "4.0", 
  "ch.qos.logback" % "logback-core" % "0.9.30", 
  "ch.qos.logback" % "logback-classic" % "0.9.30", 
  "org.slf4j" % "slf4j-api" % "1.6.2", 
  "commons-io" % "commons-io" % "2.4",
  "com.sun.jersey" % "jersey-core" % "1.18.1" ,
  "com.sun.jersey" % "jersey-server" % "1.18.1",
  "com.sun.jersey" % "jersey-client" % "1.18.1",
  "com.sun.jersey.contribs" % "jersey-multipart" % "1.18.1",
  "com.sun.jersey.contribs" % "jersey-apache-client" % "1.18.1",
  "com.sun.jersey" % "jersey-json" % "1.18.1",
  "com.sun.jersey" % "jersey-bundle" % "1.18.1",
  "org.eclipse.rdf4j" % "rdf4j-runtime" % "2.2.2" exclude("org.apache.lucene" , "lucene-core") ,
  "com.github.jsonld-java" % "jsonld-java" % "0.11.1",
  "pl.matisoft" %% "swagger-play24" % "1.4",
  "com.yourmediashelf.fedora.client" % "fedora-client" % "0.7",
  "com.yourmediashelf.fedora.client" % "fedora-client-core" % "0.7",
  "org.elasticsearch" % "elasticsearch" % "1.1.0",
  "org.antlr" % "antlr4" % "4.0",
  "javax.ws.rs" % "javax.ws.rs-api" % "2.0",
  "xmlunit" % "xmlunit" % "1.5",
  "com.sun.xml.bind" % "jaxb-impl" % "2.2.6",
  "javax.xml.bind" % "jaxb-api" % "2.2.6",
  "org.apache.ws.xmlschema" % "xmlschema" % "2.0.2",
  "net.sf.supercsv" %"super-csv" %"2.3.1",
  "com.fasterxml.jackson.core" %"jackson-core" %"2.6.3",
  "com.fasterxml" %"jackson-module-json-org" %"0.9.1",
  "com.fasterxml.jackson.core" %"jackson-databind" %"2.6.3",
  "com.fasterxml.jackson.dataformat" %"jackson-dataformat-xml" %"2.6.3",
  "javax.mail" % "mail" % "1.4.2",
  "org.apache.lucene" % "lucene-core" % "4.7.2",  
  "mysql" % "mysql-connector-java" % "8.0.23"
)

val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

resolvers := Seq(Resolver.mavenLocal,"Maven Central Server" at "https://repo1.maven.org/maven2","edoweb releases" at "https://edoweb.github.io/releases","hypnoticocelot" at "https://oss.sonatype.org/content/repositories/releases/", "aduna" at "https://maven.ontotext.com/content/repositories/aduna/" , "Typesafe repository" at "https://repo.typesafe.com/typesafe/ivy-releases/", "Duraspace releases" at "https://m2.duraspace.org/content/repositories/thirdparty", "Play2war plugins release" at "https://repository-play-war.forge.cloudbees.com/release/" )

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.withSource := true
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes 
