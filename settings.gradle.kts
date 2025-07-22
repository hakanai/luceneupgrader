rootProject.name = "luceneupgrader"
include("testgen:common")
include("testgen:lucene1")
include("testgen:lucene2")
include("testgen:lucene3")
include("testgen:lucene4")
include("testgen:lucene5")
include("testgen:lucene6")
include("testgen:lucene7")
include("testgen:lucene8")
include("testgen:lucene9")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
