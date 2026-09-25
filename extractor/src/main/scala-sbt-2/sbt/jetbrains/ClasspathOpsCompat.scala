package sbt.jetbrains

import org.jetbrains.sbt.compat.FileConverterCompat
import sbt.Attributed
import xsbti.{HashedVirtualFileRef, VirtualFile}

import java.io.File
import java.nio.file.Path as NioPath

object ClasspathOpsCompat extends ClasspathOpsCompat

trait ClasspathOpsCompat {
  type FileRef = HashedVirtualFileRef
  type Out = VirtualFile

  def toNioPath(a: Attributed[HashedVirtualFileRef])(using converter: FileConverterCompat): NioPath =
    converter.underlying.toPath(a.data)

  inline def toFile(a: Attributed[HashedVirtualFileRef])(using converter: FileConverterCompat): File =
    toNioPath(a).toFile()

  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]])(using converter: FileConverterCompat): Seq[NioPath] =
    cp.map(toNioPath).toVector

  inline def toFiles(cp: Seq[Attributed[HashedVirtualFileRef]])(using converter: FileConverterCompat): Seq[File] =
    cp.map(toFile).toVector

  inline def toAttributedFiles(cp: Seq[Attributed[HashedVirtualFileRef]])(using converter: FileConverterCompat): Seq[Attributed[File]] =
    cp.map { item =>
      val file = toFile(item)
      Attributed(file)(item.metadata)
    }
}
