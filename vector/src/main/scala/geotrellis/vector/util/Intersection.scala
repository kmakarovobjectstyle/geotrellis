/*
 * Copyright 2016 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package geotrellis.vector.util

import geotrellis.vector._

object Intersection {

  private def polyGeomIntersection(poly: Polygon, geom: Geometry): Seq[Polygon] = {
    geom match {
      case poly2: Polygon => poly2.intersection(poly) match {
        case PolygonResult(p) => Seq(p)
        case MultiPolygonResult(mp) => mp.polygons.toSeq
        case GeometryCollectionResult(gc) =>
          gc.multiPolygons.flatMap{ mp => mp.polygons.toSeq } ++ gc.polygons
        case _ => Seq.empty[Polygon]
      }
      case mp: MultiPolygon => mp.intersection(poly) match {
        case PolygonResult(p) => Seq(p)
        case MultiPolygonResult(mp) => mp.polygons.toSeq
        case GeometryCollectionResult(gc) =>
          gc.multiPolygons.flatMap{ mp => mp.polygons.toSeq } ++ gc.polygons
        case _ => Seq.empty[Polygon]
      }
      case gc: GeometryCollection => gc.intersection(poly) match {
        // In testing, GeometryCollection.intersection only seems to return GeometryCollectionResult
        case GeometryCollectionResult(res) =>
          res.multiPolygons.flatMap{ mp => mp.polygons.toSeq } ++ res.polygons
        case _ => Seq.empty[Polygon]
      }
      case _ =>
        Seq.empty
    }
  }

  /** Returns only polygonal regions of an intersection
   */
  def polygonalRegions(a: Geometry, b: Geometry): Seq[Polygon] = {
    a match {
      case poly: Polygon => polyGeomIntersection(poly, b)
      case multipoly: MultiPolygon => multipoly.polygons.toSeq.flatMap{ p => polyGeomIntersection(p, b) }
      case gc: GeometryCollection =>
        (gc.multiPolygons.flatMap(_.polygons) ++ gc.polygons).flatMap{ p => polyGeomIntersection(p, b) }
      case _ => Seq.empty[Polygon] // Both participants must contain polygonal regions
    }
  }

}
