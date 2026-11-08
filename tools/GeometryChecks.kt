package org.ods.scanner

/** Standalone checks using the installed Kotlin compiler; no Android SDK or Gradle cache needed. */
fun main() {
    check(Crop.Full.valid())
    check(Crop.Full.area() == 1f)
    check(Crop.decode(Crop.Full.encode()) == Crop.Full)
    check(!Crop(listOf(Corner(0f, 0f), Corner(1f, 1f), Corner(1f, 0f), Corner(0f, 1f))).valid())
    check(!Crop(listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(.1f, .1f), Corner(0f, 1f))).valid())
    check(!Crop(List(4) { Corner(.5f, .5f) }).valid())
    check(Crop.decode("NaN,0;1,0;1,1;0,1") == Crop.Full)
    check(safeName("../../invoice") == "_.._invoice")
    check(safeName("हिंदी invoice") == "हिंदी invoice")
    check(safeName("...") == "Document")
    check(safeName("x".repeat(200)).length == 80)
    println("11 geometry and safe-filename checks passed")
}
