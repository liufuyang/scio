/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.parquet.tensorflow

import java.lang.{Boolean => JBoolean}

import com.spotify.scio.ScioContext
import com.spotify.scio.io.{ScioIO, Tap, TapOf, TapT}
import com.spotify.scio.parquet.GcsConnectorUtil
import com.spotify.scio.util.ScioUtil
import com.spotify.scio.values.SCollection
import me.lyh.parquet.tensorflow.{ExampleParquetInputFormat, Schema}
import org.apache.beam.sdk.io.hadoop.format.HadoopFormatIO
import org.apache.beam.sdk.transforms.SimpleFunction
import org.apache.hadoop.mapreduce.Job
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.hadoop.ParquetInputFormat
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.tensorflow.example.Example

import scala.collection.JavaConverters._

final case class ParquetExampleIO(path: String) extends ScioIO[Example] {
  override type ReadP = ParquetExampleIO.ReadParam
  override type WriteP = ParquetExampleIO.WriteParam
  override val tapT = TapOf[Example]

  override protected def read(sc: ScioContext, params: ReadP): SCollection[Example] = {
    val job = Job.getInstance()
    GcsConnectorUtil.setInputPaths(sc, job, path)
    job.setInputFormatClass(classOf[ExampleParquetInputFormat])
    job.getConfiguration.setClass("key.class", classOf[Void], classOf[Void])
    job.getConfiguration.setClass("value.class", classOf[Example], classOf[Example])

    if (params.projection != null) {
      ExampleParquetInputFormat.setFields(job, params.projection.asJava)
    }
    if (params.predicate != null) {
      ParquetInputFormat.setFilterPredicate(job.getConfiguration, params.predicate)
    }

    val source = HadoopFormatIO
        .read[JBoolean, Example]()
        .withKeyTranslation(new SimpleFunction[Void, JBoolean]() {
          override def apply(input: Void): JBoolean = true // workaround for NPE
        })
        .withConfiguration(job.getConfiguration)
    sc.wrap(sc.applyInternal(source)).map(_.getValue)
  }

  override protected def write(data: SCollection[Example], params: WriteP): Tap[Example] = {
    val job = Job.getInstance()
    if (ScioUtil.isLocalRunner(data.context.options.getRunner)) {
      GcsConnectorUtil.setCredentials(job)
    }


    ???
  }

  override def tap(read: ReadP): Tap[tapT.T] = ???
}

object ParquetExampleIO {
  final case class ReadParam private (
    projection: Seq[String] = null,
    predicate: FilterPredicate = null
  )

  object WriteParam {
    private[tensorflow] val DefaultSchema = null
    private[tensorflow] val DefaultNumShards = 0
    private[tensorflow] val DefaultSuffix = ".parquet"
    private[tensorflow] val DefaultCompression = CompressionCodecName.SNAPPY
  }

  final case class WriteParam private (
    schema: Schema = WriteParam.DefaultSchema,
    numShards: Int = WriteParam.DefaultNumShards,
    suffix: String = WriteParam.DefaultSuffix,
    compression: CompressionCodecName = WriteParam.DefaultCompression
  )
}
