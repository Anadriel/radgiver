package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.GcsConfig
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import zio.{RLayer, Scope, ZIO, ZLayer}

import java.nio.ByteBuffer

trait GcsService {
  def readBytes(path: String): ZIO[Any, Throwable, Option[Array[Byte]]]

  def writeBytes(
    path: String,
    content: Array[Byte],
    overwrite: Boolean = true,
  ): ZIO[Any, Throwable, Unit]

}

object GcsService {

  private class LiveGcsService(storage: Storage, config: GcsConfig) extends GcsService {

    override def readBytes(path: String): ZIO[Any, Throwable, Option[Array[Byte]]] = {
      val blobId = BlobId.of(config.bucketName, path)
      ZIO.attemptBlocking(
        Option(storage.get(blobId)).map(_.getContent())
      )
    }

    private def createBlob(blobId: BlobId, content: Array[Byte]): ZIO[Any, Throwable, Unit] = {
      val blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build
      ZIO.attemptBlocking(storage.create(blobInfo, content)).map(_ => ())
    }

    override def writeBytes(
      path: String,
      content: Array[Byte],
      overwrite: Boolean,
    ): ZIO[Any, Throwable, Unit] = {
      val blobId = BlobId.of(config.bucketName, path)
      if (overwrite)
        createBlob(blobId, content)
      else {
        for {
          blob <- ZIO.attemptBlocking(storage.get(blobId))
          _ <-
            if (Option(blob).isDefined)
              ZIO.attemptBlocking {
                val channel = blob.writer()
                channel.write(ByteBuffer.wrap(blob.getContent() ++ content))
                channel.close()
              }
            else
              createBlob(blobId, content)
        } yield ()
      }
    }

  }

  val layer: RLayer[Scope with GcsConfig, GcsService] = ZLayer {
    for {
      config <- ZIO.service[GcsConfig]
      storage <- ZIO.fromAutoCloseable(
        ZIO.attemptBlocking(
          StorageOptions.newBuilder.setProjectId(config.project).build.getService
        )
      )
    } yield LiveGcsService(storage, config)
  }

}
