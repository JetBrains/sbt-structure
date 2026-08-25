managedScalaInstance := false
scalaInstance := {
  throw new RuntimeException("Custom scalaInstance error for testing")
}
