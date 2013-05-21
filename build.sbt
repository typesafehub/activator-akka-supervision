name := "hello-scala"

version := "1.0"

scalaVersion := "2.10.1"

resolvers += "Typesafe Snapshot Repository" at "http://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2-M3"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.2-M3"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"


// Note: These settings are defaults for activator, and reorganize your source directories.
Seq(
  scalaSource in Compile <<= baseDirectory / "app",
  javaSource in Compile <<= baseDirectory / "app",
  sourceDirectory in Compile <<= baseDirectory / "app",
  scalaSource in Test <<= baseDirectory / "test",
  javaSource in Test <<= baseDirectory / "test",
  sourceDirectory in Test <<= baseDirectory / "test",
  resourceDirectory in Compile <<= baseDirectory / "conf"
)
