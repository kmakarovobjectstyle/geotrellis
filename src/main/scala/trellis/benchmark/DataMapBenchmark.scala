package trellis.benchmark


import trellis._

import scala.util.Random

object DataMapBenchmark {

  val warmup = 50
  val times = 400
  val h = 1024
  val w = 1024

  val n = h * w

  def array1(data:Array[Int]) = {
    val data2 = data.clone
    var i = 0
    val len = data2.length
    while (i < len) {
      data2(i) = data(i) * 2
      i += 1
    }
    data2
  }

  def array2(data:Array[Int]) = {
    val data2 = data.clone
    var i = 0
    val len = data2.length
    while (i < len) {
      data2(i) = data(i) * 2
      i += 1
    }
    data2
  }

  def direct1(raster:IntRaster) = {
    val data = raster.data
    val raster2 = raster.copy
    val data2 = raster2.data
    var i = 0
    val len = data2.length
    while (i < len) {
      data2(i) = data(i) * 2
      i += 1
    }

    raster2
  }

  def direct2(raster:IntRaster) = {
    val data = raster.data
    val raster2 = raster.copy
    val data2 = raster2.data
    var i = 0
    val len = data2.length
    while (i < len) {
      data2(i) = data(i) * 2
      i += 1
    }
    raster2
  }

  def indirect1(raster:IntRaster) = raster.map(z => z * 2)
  def indirect2(raster:IntRaster) = raster.map(z => z + 16)

  def main(args:Array[String]) {
    var arrayTime = 0L
    var directTime = 0L
    var indirectTime = 0L

    val data = Array.ofDim[Int](n).map(i => Random.nextInt())
    val extent = Extent(0, 0, w, h)
    val geo = RasterExtent(extent, 1.0, 1.0, w, h)
    val raster = IntRaster(data, h, w, geo)

    var currRaster = raster
    var currData = data

    def runit() {
      val t0 = System.currentTimeMillis()
      currData = array1(data)
      arrayTime += System.currentTimeMillis() - t0

      val t1 = System.currentTimeMillis()
      currRaster = direct1(raster)
      directTime += System.currentTimeMillis() - t1

      val t2 = System.currentTimeMillis()
      currRaster = indirect1(raster)
      indirectTime += System.currentTimeMillis() - t2

      val t3 = System.currentTimeMillis()
      currData = array1(data)
      arrayTime += System.currentTimeMillis() - t3

      val t4 = System.currentTimeMillis()
      currRaster = direct2(raster)
      directTime += System.currentTimeMillis() - t4

      val t5 = System.currentTimeMillis()
      currRaster = indirect2(raster)
      indirectTime += System.currentTimeMillis() - t5
    }

    println("doing %d warmup iterations" format warmup)
    for (i <- 0 until warmup) {
      runit()
      System.gc()
    }

    arrayTime = 0L
    directTime = 0L
    indirectTime = 0L

    println("running each test %d times" format (times * 2))
    for (i <- 0 until times) {
      runit()
    }

    output("array", arrayTime)
    output("direct", directTime)
    output("indirect", indirectTime)
  }

  def output(label:String, t:Long) {
    val p = (t * 1.0) / (times * 2)
    println("%-10s took %4d ms (%.3f ms/per)" format (label, t, p))
  }
}
