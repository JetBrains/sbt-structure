package sbt.jetbrains

import org.jetbrains.sbt.compat.FileConverterCompat
import sbt.Attributed

import java.io.File
import java.nio.file.Path as NioPath

object ClasspathOpsCompat extends ClasspathOpsCompat

trait ClasspathOpsCompat {
  type FileRef = java.io.File
  type Out = java.io.File

  def toNioPath(a: Attributed[File])(implicit converter: FileConverterCompat): NioPath =
    a.data.toPath()

  def toFile(a: Attributed[File])(implicit converter: FileConverterCompat): File =
    a.data

  def toNioPaths(cp: Seq[Attributed[File]])(implicit converter: FileConverterCompat): Vector[NioPath] =
    cp.map(_.data.toPath()).toVector

  def toFiles(cp: Seq[Attributed[File]])(implicit converter: FileConverterCompat): Vector[File] =
    cp.map(_.data).toVector

  def toAttributedFiles(cp: Seq[Attributed[File]])(implicit converter: FileConverterCompat): Seq[Attributed[File]] =
    cp
}
