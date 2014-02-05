package geotrellis.spark.cmd
import geotrellis._
import geotrellis.Raster
import geotrellis.RasterExtent
import geotrellis.data.GeoTiffWriter
import geotrellis.raster.TileLayout
import geotrellis.spark.formats.ArgWritable
import geotrellis.spark.formats.TileIdWritable
import geotrellis.spark.metadata.PyramidMetadata
import geotrellis.spark.rdd.RasterHadoopRDD
import geotrellis.spark.storage.RasterReader
import geotrellis.spark.tiling.TmsTiling
import geotrellis.spark.utils.SparkUtils
import org.apache.hadoop.fs.Path
import org.apache.spark.Logging
import java.io.File
import com.quantifind.sumac.ArgMain
import com.quantifind.sumac.validation.Positive

/**
 * @author akini
 *
 * Export a raster as GeoTIFF either as a single tiff or tiff-per-tile.
 * The tiff-per-tile use case uses Spark to read the tiles
 *
 *
 * Export 	[--single <boolean>]
 * 			--input <path-to-pyramid>
 *    		--zoom <zoom>
 *      	--output <path-to-dir-or-file>
 *       	[--sparkMaster <spark-master-ip>]
 *
 * Single tiff
 * Export --single true --input file:///tmp/all-ones --zoom 10 --output /tmp/all-ones-export.tif
 *
 * tiff-per-tile
 * Export --input file:///tmp/all-ones --zoom 10 --output /tmp/all-ones-export --sparkMaster local
 *
 * Constraints:
 *
 * --single <boolean> - this is either true or false (default) depending on whether the output needs
 * to be a single merged tiff or tiff-per-tile
 *
 * --input <path-to-pyramid> - this can be either on hdfs (hdfs://) or local fs (file://) and is a fully
 * qualified path to the pyramid
 *
 * --output <path-to-dir-or-file> - this is a file in the case of a single tiff, and a directory in
 * the case of tiff-per-tile. Either way, the output would be on the local file system.
 *
 * --sparkMaster <spark-master-ip> - this is the conventional spark cluster url
 * 	(e.g. spark://host:port, local, local[4])
 *
 */
class ExportArgs extends CommandArguments {
  var single: Boolean = false
  @Positive var zoom: Int = _
}

object Export extends ArgMain[ExportArgs] with Logging {

  type TileIdCoord = Tuple3[Long, Long, Long] // [tileId, tx, ty]
  type TileIdRaster = Tuple2[TileIdCoord, Raster]

  def main(args: ExportArgs) {
    val rasterPath = new Path(args.input)
    val zoom = args.zoom
    val rasterPathWithZoom = new Path(rasterPath, zoom.toString)
    val outputDir = args.output
    val sparkMaster = args.sparkMaster

    if (args.single)
      exportSingle(rasterPathWithZoom, outputDir)
    else
      exportTiles(rasterPathWithZoom, outputDir, sparkMaster)

  }

  // TODO - this should probably go into the RasterReader as well as RasterHadoopRDD as an implicit
  private def toRaster(writable: Tuple2[TileIdWritable, ArgWritable],
                       meta: PyramidMetadata, zoom: Int): TileIdRaster = {
    val (tileSize, rasterType) = (meta.tileSize, meta.rasterType)
    val (tw, aw) = writable
    val tileId = tw.get
    val (tx, ty) = TmsTiling.tileXY(tileId, zoom)
    val extent = TmsTiling.tileToExtent(tx, ty, zoom, tileSize)
    val rd = aw.toRasterData(rasterType, tileSize, tileSize)
    val trd = NoDataHandler.removeGeotrellisNoData(rd, meta.nodata)
    val raster = Raster(trd, RasterExtent(extent, tileSize, tileSize))
    ((tileId, tx, ty), raster)
  }

  private def exportSingle(rasterPath: Path, output: String) {
    val conf = SparkUtils.createHadoopConfiguration
    val meta = PyramidMetadata(rasterPath.getParent, conf)
    val zoom = rasterPath.getName.toInt
    val (tileSize, rasterType) = (meta.tileSize, meta.rasterType)

    // get extents and layout
    val tileExtent = meta.rasterMetadata(zoom.toString).tileExtent
    val res = TmsTiling.resolution(zoom, tileSize)
    val rasterExtent = RasterExtent(TmsTiling.tileToExtent(tileExtent, zoom, tileSize), res, res)
    val layout = TileLayout(tileExtent.width.toInt, tileExtent.height.toInt, tileSize, tileSize)

    // open the reader
    val reader = RasterReader(rasterPath, conf)

    // TMS tiles start from lower left corner whereas TileRaster expects them to start from  
    // upper left, so we need to re-sort the array 
    def compare(left: TileIdRaster, right: TileIdRaster): Boolean =
      (left._1._3 > right._1._3) || (left._1._3 == right._1._3 && left._1._2 < right._1._2)
    val (tileIds, tiles) = reader.map(toRaster(_, meta, zoom)).toList.sortWith(compare).unzip
    reader.close()

    val raster = TileRaster(tiles, rasterExtent, layout).toArrayRaster
    GeoTiffWriter.write(s"${output}", raster, meta.nodata)
    logInfo(s"---------finished writing to file ${output}")
  }

  private def exportTiles(rasterPath: Path, output: String, sparkMaster: String) {
    logInfo(s"Deleting and creating output directory: $output")
    val dir = new File(output)
    dir.delete()
    dir.mkdirs()

    val sc = SparkUtils.createSparkContext(sparkMaster, "Export")

    try {
      val meta = PyramidMetadata(rasterPath.getParent, sc.hadoopConfiguration)
      val raster = RasterHadoopRDD(sc, rasterPath.toUri.toString)
      val zoom = rasterPath.getName.toInt

      raster.foreach(t => {
        val (tileIdCoord, raster) = toRaster(t, meta, zoom)
        val (tileId, tx, ty) = tileIdCoord
        GeoTiffWriter.write(s"${output}/tile-${tileId}.tif", raster, meta.nodata)
        logInfo(s"---------tx: $tx, ty: $ty file: tile-${tileId}.tif")
      })

      logInfo(s"Exported ${raster.count} tiles to $output")
    } finally {
      sc.stop
      System.clearProperty("spark.master.port")
    }
  }
}