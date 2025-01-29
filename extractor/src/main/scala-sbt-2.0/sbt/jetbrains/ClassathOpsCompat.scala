package sbt.jetbrains

import sbt.Attributed
import xsbti.{FileConverter, HashedVirtualFileRef, VirtualFile}

import java.io.File
import java.nio.file.Path as NioPath

object ClassathOpsCompat extends ClassathOpsCompat

trait ClassathOpsCompat {
  type FileRef = HashedVirtualFileRef
  type Out = VirtualFile

  private val conv: FileConverter = sbt.internal.inc.PlainVirtualFileConverter.converter

  def toNioPath(a: Attributed[HashedVirtualFileRef]): NioPath =
    conv.toPath(a.data)

  inline def toFile(a: Attributed[HashedVirtualFileRef]): File =
    toNioPath(a).toFile()

  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]]): Seq[NioPath] =
    cp.map(toNioPath).toVector

  inline def toFiles(cp: Seq[Attributed[HashedVirtualFileRef]]): Seq[File] =
    toNioPaths(cp).map(_.toFile())

  inline def toAttributedFiles(cp: Seq[Attributed[HashedVirtualFileRef]]): Seq[Attributed[File]] =
    cp.map { item =>
      val file = conv.toPath(item.data).toFile
      Attributed(file)(item.metadata)
    }
}

