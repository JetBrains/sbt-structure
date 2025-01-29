package sbt.jetbrains

import sbt.*
import sbt.Scoped.ScopingSetting

import scala.collection.Seq

object ScopeOpsCompat extends ScopeOpsCompat
trait ScopeOpsCompat:
  //TODO: drop "in" syntax and use sbt 1.x syntax
  extension [Result](scoping: ScopingSetting[Result])
    def in(s: Scope): Result = s / scoping
    def in(p: Reference): Result = Select(p) / scoping
    def in(t: Scoped): Result = This / This / Select(t.key) / scoping
    def in(c: ConfigKey): Result = This / Select(c) / This / scoping
    def in(c: ConfigKey, t: Scoped): Result = This / Select(c) / Select(t.key) / scoping
    def in(p: Reference, c: ConfigKey): Result = Select(p) / Select(c) / This / scoping
    def in(p: Reference, t: Scoped): Result = Select(p) / This / Select(t.key) / scoping
    def in(p: Reference, c: ConfigKey, t: Scoped): Result = Select(p) / Select(c) / Select(t.key) / scoping
    def in(p: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]]): Result = Scope(p, c, t, This) / scoping