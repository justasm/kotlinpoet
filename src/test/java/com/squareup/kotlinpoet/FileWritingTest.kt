/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.kotlinpoet

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.Date
import javax.lang.model.element.Element

class FileWritingTest {
  // Used for testing java.io File behavior.
  @JvmField @Rule val tmp = TemporaryFolder()

  // Used for testing java.nio.file Path behavior.
  private val fs = Jimfs.newFileSystem(Configuration.unix())
  private val fsRoot = fs.rootDirectories.iterator().next()

  // Used for testing annotation processor Filer behavior.
  private val filer = TestFiler(fs, fsRoot)

  @Test fun pathNotDirectory() {
    val type = TypeSpec.classBuilder("Test").build()
    val kotlinFile = KotlinFile.get("example", type)
    val path = fs.getPath("/foo/bar")
    Files.createDirectories(path.parent)
    Files.createFile(path)
    try {
      kotlinFile.writeTo(path)
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected.message).isEqualTo("path /foo/bar exists but is not a directory.")
    }
  }

  @Test fun fileNotDirectory() {
    val type = TypeSpec.classBuilder("Test").build()
    val kotlinFile = KotlinFile.get("example", type)
    val file = File(tmp.newFolder("foo"), "bar")
    file.createNewFile()
    try {
      kotlinFile.writeTo(file)
      fail()
    } catch (expected: IllegalArgumentException) {
      assertThat(expected.message).isEqualTo(
          "path ${file.path} exists but is not a directory.")
    }

  }

  @Test fun pathDefaultPackage() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("", type).writeTo(fsRoot)

    val testPath = fsRoot.resolve("Test.kt")
    assertThat(Files.exists(testPath)).isTrue()
  }

  @Test fun fileDefaultPackage() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("", type).writeTo(tmp.root)

    val testFile = File(tmp.root, "Test.kt")
    assertThat(testFile.exists()).isTrue()
  }

  @Test fun filerDefaultPackage() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("", type).writeTo(filer)

    val testPath = fsRoot.resolve("Test.kt")
    assertThat(Files.exists(testPath)).isTrue()
  }

  @Test fun pathNestedClasses() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("foo", type).writeTo(fsRoot)
    KotlinFile.get("foo.bar", type).writeTo(fsRoot)
    KotlinFile.get("foo.bar.baz", type).writeTo(fsRoot)

    val fooPath = fsRoot.resolve(fs.getPath("foo", "Test.kt"))
    val barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.kt"))
    val bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.kt"))
    assertThat(Files.exists(fooPath)).isTrue()
    assertThat(Files.exists(barPath)).isTrue()
    assertThat(Files.exists(bazPath)).isTrue()
  }

  @Test fun fileNestedClasses() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("foo", type).writeTo(tmp.root)
    KotlinFile.get("foo.bar", type).writeTo(tmp.root)
    KotlinFile.get("foo.bar.baz", type).writeTo(tmp.root)

    val fooDir = File(tmp.root, "foo")
    val fooFile = File(fooDir, "Test.kt")
    val barDir = File(fooDir, "bar")
    val barFile = File(barDir, "Test.kt")
    val bazDir = File(barDir, "baz")
    val bazFile = File(bazDir, "Test.kt")
    assertThat(fooFile.exists()).isTrue()
    assertThat(barFile.exists()).isTrue()
    assertThat(bazFile.exists()).isTrue()
  }

  @Test fun filerNestedClasses() {
    val type = TypeSpec.classBuilder("Test").build()
    KotlinFile.get("foo", type).writeTo(filer)
    KotlinFile.get("foo.bar", type).writeTo(filer)
    KotlinFile.get("foo.bar.baz", type).writeTo(filer)

    val fooPath = fsRoot.resolve(fs.getPath("foo", "Test.kt"))
    val barPath = fsRoot.resolve(fs.getPath("foo", "bar", "Test.kt"))
    val bazPath = fsRoot.resolve(fs.getPath("foo", "bar", "baz", "Test.kt"))
    assertThat(Files.exists(fooPath)).isTrue()
    assertThat(Files.exists(barPath)).isTrue()
    assertThat(Files.exists(bazPath)).isTrue()
  }

  @Test fun filerPassesOriginatingElements() {
    val element1_1 = Mockito.mock(Element::class.java)
    val test1 = TypeSpec.classBuilder("Test1")
        .addOriginatingElement(element1_1)
        .build()

    val element2_1 = Mockito.mock(Element::class.java)
    val element2_2 = Mockito.mock(Element::class.java)
    val test2 = TypeSpec.classBuilder("Test2")
        .addOriginatingElement(element2_1)
        .addOriginatingElement(element2_2)
        .build()

    KotlinFile.get("example", test1).writeTo(filer)
    KotlinFile.get("example", test2).writeTo(filer)

    val testPath1 = fsRoot.resolve(fs.getPath("example", "Test1.kt"))
    assertThat(filer.getOriginatingElements(testPath1)).containsExactly(element1_1)
    val testPath2 = fsRoot.resolve(fs.getPath("example", "Test2.kt"))
    assertThat(filer.getOriginatingElements(testPath2)).containsExactly(element2_1, element2_2)
  }

  @Test fun filerClassesWithTabIndent() {
    val test = TypeSpec.classBuilder("Test")
        .addProperty(Date::class, "madeFreshDate")
        .addFun(FunSpec.builder("main")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(Array<String>::class, "args")
            .addCode("%T.out.println(%S);\n", System::class, "Hello World!")
            .build())
        .build()
    KotlinFile.builder("foo", "Test")
        .addType(test)
        .indent("\t")
        .build()
        .writeTo(filer)

    val fooPath = fsRoot.resolve(fs.getPath("foo", "Test.kt"))
    assertThat(Files.exists(fooPath)).isTrue()
    val source = String(Files.readAllBytes(fooPath))

    assertThat(source).isEqualTo("""
        |package foo
        |
        |import java.lang.String
        |import java.lang.System
        |import java.util.Date
        |import kotlin.Array
        |
        |class Test {
        |${"\t"}val madeFreshDate: Date
        |
        |${"\t"}fun main(args: Array<String>) {
        |${"\t\t"}System.out.println("Hello World!");
        |${"\t"}}
        |}
        |""".trimMargin())
  }

  /**
   * This test confirms that KotlinPoet ignores the host charset and always uses UTF-8. The host
   * charset is customized with `-Dfile.encoding=ISO-8859-1`.
   */
  @Test fun fileIsUtf8() {
    val kotlinFile = KotlinFile.builder("foo", "Taco")
        .addType(TypeSpec.classBuilder("Taco").build())
        .addFileComment("Pi\u00f1ata\u00a1")
        .build()
    kotlinFile.writeTo(fsRoot)

    val fooPath = fsRoot.resolve(fs.getPath("foo", "Taco.kt"))
    assertThat(String(Files.readAllBytes(fooPath), UTF_8)).isEqualTo("""
        |// Piñata¡
        |package foo
        |
        |class Taco {
        |}
        |""".trimMargin())
  }
}
