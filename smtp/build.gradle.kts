dependencies {
  api(project(":server"))
  implementation(project(":i18n"))
  api("com.sun.mail:javax.mail:1.6.2")
  testImplementation(files("../sample/i18n"))
}
