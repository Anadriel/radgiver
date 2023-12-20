package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.GcsConfig
import com.google.cloud.storage.Storage.BlobTargetOption
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import zio.{RLayer, Scope, ZIO, ZLayer}

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

    override def writeBytes(
      path: String,
      content: Array[Byte],
      overwrite: Boolean,
    ): ZIO[Any, Throwable, Unit] = {
      val blobId = BlobId.of(config.bucketName, path)
      val blobInfo = BlobInfo.newBuilder(blobId).build
      val options =
        if (overwrite) List.empty
        else List(BlobTargetOption.doesNotExist())

      ZIO.attemptBlocking(storage.create(blobInfo, content, options*)).unit
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
