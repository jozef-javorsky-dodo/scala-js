/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.testsuite.niobuffer

import java.nio.{ReadOnlyBufferException, BufferUnderflowException, InvalidMarkException, BufferOverflowException}

import org.junit.Test
import org.junit.Assert._
import org.junit.Assume._

import org.scalajs.testsuite.niobuffer.ByteBufferFactories.SlicedAllocByteBufferFactory

import org.scalajs.testsuite.utils.AssertThrows.assertThrows
import org.scalajs.testsuite.utils.Platform._

abstract class BaseBufferTest {

  type Factory <: BufferFactory

  val factory: Factory

  import factory._

  @Test def allocate(): Unit = {
    val buf = allocBuffer(10)
    assertEquals(0, buf.position)
    assertEquals(10, buf.limit)
    assertEquals(10, buf.capacity)

    assertEquals(0, allocBuffer(0).capacity)

    assertThrows(classOf[IllegalArgumentException], allocBuffer(-1))
    assertThrows(classOf[IllegalArgumentException], allocBuffer(-100))

    assertThrows(classOf[Throwable], allocBuffer(0, -1, 1))
    assertThrows(classOf[Throwable], allocBuffer(1, 0, 1))
    assertThrows(classOf[Throwable], allocBuffer(0, 1, 0))
    assertThrows(classOf[Throwable], allocBuffer(1, 0, 0))

    val buf2 = allocBuffer(1, 5, 9)
    assertEquals(1, buf2.position())
    assertEquals(5, buf2.limit())
    assertEquals(9, buf2.capacity())
  }

  @Test def isReadOnly()(): Unit = {
    val buf = allocBuffer(10)
    if (createsReadOnly)
      assertTrue(buf.isReadOnly())
    else
      assertFalse(buf.isReadOnly())
  }

  @Test def position(): Unit = {
    val buf = allocBuffer(10)
    buf.position(3)
    assertEquals(3, buf.position())
    buf.position(10)
    assertEquals(10, buf.position())
    buf.position(0)
    assertEquals(0, buf.position())

    assertThrows(classOf[IllegalArgumentException], buf.position(-1))
    assertThrows(classOf[IllegalArgumentException], buf.position(11))
    assertEquals(0, buf.position())

    val buf2 = allocBuffer(1, 5, 9)
    assertEquals(1, buf2.position())

    buf2.position(5)
    assertEquals(5, buf2.position())
    assertThrows(classOf[IllegalArgumentException], buf2.position(6))
    assertEquals(5, buf2.position())
  }

  @Test def limit(): Unit = {
    val buf = allocBuffer(10)
    buf.position(3)
    buf.limit(7)
    assertEquals(7, buf.limit())
    assertEquals(3, buf.position())
    assertThrows(classOf[IllegalArgumentException], buf.limit(11))
    assertEquals(7, buf.limit())
    assertThrows(classOf[IllegalArgumentException], buf.limit(-1))
    assertEquals(7, buf.limit())
    assertEquals(3, buf.position())

    buf.position(5)
    buf.limit(4)
    assertEquals(4, buf.limit())
    assertEquals(4, buf.position())
  }

  @Test def markAndReset(): Unit = {
    val buf = allocBuffer(10)

    // Initially, the mark should not be set
    assertThrows(classOf[InvalidMarkException], buf.reset())

    // Simple test
    buf.position(3)
    buf.mark()
    buf.position(8)
    buf.reset()
    assertEquals(3, buf.position())

    // reset() should not have cleared the mark
    buf.position(5)
    buf.reset()
    assertEquals(3, buf.position())

    // setting position() below the mark should clear the mark
    buf.position(2)
    assertThrows(classOf[InvalidMarkException], buf.reset())
  }

  @Test def clear(): Unit = {
    val buf = allocBuffer(3, 6, 10)
    buf.mark()
    buf.position(4)

    buf.clear()
    assertEquals(0, buf.position())
    assertEquals(10, buf.limit()) // the capacity
    assertEquals(10, buf.capacity())
    assertThrows(classOf[InvalidMarkException], buf.reset())
  }

  @Test def flip(): Unit = {
    val buf = allocBuffer(3, 6, 10)
    buf.mark()
    buf.position(4)

    buf.flip()
    assertEquals(0, buf.position())
    assertEquals(4, buf.limit()) // old position
    assertEquals(10, buf.capacity())
    assertThrows(classOf[InvalidMarkException], buf.reset())
  }

  @Test def rewind(): Unit = {
    val buf = allocBuffer(3, 6, 10)
    buf.mark()
    buf.position(4)

    buf.rewind()
    assertEquals(0, buf.position())
    assertEquals(6, buf.limit()) // unchanged
    assertEquals(10, buf.capacity())
    assertThrows(classOf[InvalidMarkException], buf.reset())
  }

  @Test def remainingAndHasRemaining(): Unit = {
    val buf = allocBuffer(3, 7, 10)
    assertEquals(7 - 3, buf.remaining())

    assertTrue(buf.hasRemaining())

    buf.position(6)
    assertEquals(7 - 6, buf.remaining())
    assertTrue(buf.hasRemaining())

    buf.limit(9)
    assertEquals(9 - 6, buf.remaining())
    assertTrue(buf.hasRemaining())

    buf.limit(2)
    assertEquals(0, buf.remaining())
    assertFalse(buf.hasRemaining())

    buf.position(0)
    assertEquals(2, buf.remaining())
    assertTrue(buf.hasRemaining())
  }

  @Test def absoluteGet(): Unit = {
    val buf = withContent(10, elemRange(0, 10): _*)
    assertEquals(elemFromInt(0), buf.get(0))
    assertEquals(0, buf.position())
    assertEquals(elemFromInt(3), buf.get(3))
    assertEquals(0, buf.position())

    assertThrows(classOf[IndexOutOfBoundsException], buf.get(-1))
    assertThrows(classOf[IndexOutOfBoundsException], buf.get(15))

    buf.limit(4)
    assertThrows(classOf[IndexOutOfBoundsException], buf.get(5))
  }

  @Test def absolutePut(): Unit = {
    val buf = allocBuffer(10)
    if (!createsReadOnly) {
      buf.put(5, 42)
      assertEquals(0, buf.position())
      buf.put(3, 2)
      assertEquals(elemFromInt(2), buf.get(3))
      assertEquals(elemFromInt(42), buf.get(5))
      assertEquals(elemFromInt(0), buf.get(7))

      assertThrows(classOf[IndexOutOfBoundsException], buf.put(-1, 2))
      assertThrows(classOf[IndexOutOfBoundsException], buf.put(14, 9))

      buf.limit(4)
      assertThrows(classOf[IndexOutOfBoundsException], buf.put(4, 1))
    } else {
      assertThrows(classOf[ReadOnlyBufferException], buf.put(2, 1))
      assertEquals(elemFromInt(0), buf.get(2))
      assertEquals(0, buf.position())

      assertThrows(classOf[ReadOnlyBufferException], buf.put(-2, 1))
      assertThrows(classOf[ReadOnlyBufferException], buf.put(12, 1))
    }
  }

  @Test def relativeGet(): Unit = {
    val buf = withContent(10, elemRange(0, 10): _*)
    assertEquals(elemFromInt(0), buf.get())
    assertEquals(1, buf.position())
    buf.position(3)
    assertEquals(elemFromInt(3), buf.get())
    assertEquals(4, buf.position())

    buf.limit(4)
    assertThrows(classOf[BufferUnderflowException], buf.get())
  }

  @Test def relativePut(): Unit = {
    val buf = allocBuffer(10)
    if (!createsReadOnly) {
      buf.put(5)
      assertEquals(1, buf.position())
      assertEquals(elemFromInt(5), buf.get(0))

      buf.position(3)
      buf.put(36)
      assertEquals(4, buf.position())
      assertEquals(elemFromInt(36), buf.get(3))

      buf.position(10)
      assertThrows(classOf[BufferOverflowException], buf.put(3))
    } else {
      assertThrows(classOf[ReadOnlyBufferException], buf.put(5))
      assertEquals(0, buf.position())
      assertEquals(elemFromInt(0), buf.get(0))

      buf.position(10)
      assertThrows(classOf[ReadOnlyBufferException], buf.put(3))
    }
  }

  @Test def relativeBulkGet(): Unit = {
    val buf = withContent(10, elemRange(0, 10): _*)
    val a = new Array[ElementType](4)
    buf.get(a)
    assertArrayEquals(boxedElemsFromInt(0, 1, 2, 3), boxed(a))
    assertEquals(4, buf.position())

    buf.position(6)
    buf.get(a, 1, 2)
    assertArrayEquals(boxedElemsFromInt(0, 6, 7, 3), boxed(a))
    assertEquals(8, buf.position())

    assertThrows(classOf[Exception], buf.get(a))
    assertEquals(8, buf.position())
    assertArrayEquals(boxedElemsFromInt(0, 6, 7, 3), boxed(a))
  }

  @Test def relativeBulkPut(): Unit = {
    val buf = allocBuffer(10)
    if (!createsReadOnly) {
      buf.put(Array[ElementType](6, 7, 12))
      assertArrayEquals(boxedElemsFromInt(6, 7, 12, 0), boxed((0 to 3).map(buf.get).toArray))
      assertEquals(3, buf.position())

      buf.position(2)
      buf.put(Array[ElementType](44, 55, 66, 77, 88), 2, 2)
      assertArrayEquals(boxedElemsFromInt(6, 7, 66, 77, 0), boxed((0 to 4).map(buf.get).toArray))
      assertEquals(4, buf.position())

      assertThrows(classOf[BufferOverflowException], buf.put(Array.fill[ElementType](10)(0)))
      assertEquals(4, buf.position())
      assertArrayEquals(boxedElemsFromInt(6, 7, 66, 77, 0), boxed((0 to 4).map(buf.get).toArray))
    } else {
      assertThrows(classOf[ReadOnlyBufferException], buf.put(Array[ElementType](6, 7, 12)))
      assertEquals(0, buf.position())
      assertEquals(elemFromInt(0), buf.get(0))

      /* Trying to put an array too large into a read-only buffer can either
       * throw a ReadOnlyBufferException or a BufferOverflowException. The spec
       * does not favor one or the other, and on the JVM, in some cases it is
       * one and in some other cases the other.
       */
      buf.position(8)
      val exception =
        assertThrows(classOf[RuntimeException], buf.put(Array[ElementType](6, 7, 12)))
      assertTrue(
          exception.isInstanceOf[ReadOnlyBufferException] ||
          exception.isInstanceOf[BufferOverflowException])
      assertEquals(8, buf.position())
      assertEquals(elemFromInt(0), buf.get(8))
    }
  }

  @Test def compact(): Unit = {
    assumeFalse("Affected by a bug in the JDK.",
        executingInJVMOnLowerThanJDK(9) &&
        factory.isInstanceOf[BufferFactory.ByteBufferViewFactory])

    if (!createsReadOnly) {
      val buf = withContent(10, elemRange(0, 10): _*)
      buf.position(6)
      buf.mark()

      buf.compact()
      assertEquals(4, buf.position())
      assertEquals(10, buf.limit())
      assertThrows(classOf[InvalidMarkException], buf.reset())

      for (i <- 0 until 4)
        assertEquals(elemFromInt(i + 6), buf.get(i))
    } else {
      val buf = allocBuffer(10)
      assertThrows(classOf[ReadOnlyBufferException], buf.compact())
    }
  }

  @Test def slice(): Unit = {
    val buf1 = withContent(10, elemRange(0, 10): _*)
    buf1.position(3)
    buf1.limit(7)
    buf1.mark()
    val buf2 = buf1.sliceChain()
    assertEquals(0, buf2.position())
    assertEquals(4, buf2.limit())
    assertEquals(4, buf2.capacity())
    assertThrows(classOf[InvalidMarkException], buf2.reset())

    assertEquals(elemFromInt(4), buf2.get(1))

    buf2.position(2)
    assertEquals(3, buf1.position())

    if (!createsReadOnly) {
      buf2.put(89)
      assertEquals(elemFromInt(89), buf1.get(5))
      assertEquals(3, buf2.position())
      assertEquals(3, buf1.position())
    }

    assertThrows(classOf[IllegalArgumentException], buf2.limit(5))
    assertEquals(4, buf2.limit())

    buf2.limit(3)
    assertEquals(7, buf1.limit())

    if (!createsReadOnly) {
      buf1.put(3, 23)
      assertEquals(elemFromInt(23), buf2.get(0))
    }
  }

  @Test def duplicate(): Unit = {
    val buf1 = withContent(10, elemRange(0, 10): _*)
    buf1.position(3)
    buf1.limit(7)
    buf1.mark()
    val buf2 = buf1.duplicateChain()
    assertEquals(3, buf2.position())
    assertEquals(7, buf2.limit())
    assertEquals(10, buf2.capacity())
    assertEquals(elemFromInt(4), buf2.get(4))

    buf2.position(4)
    assertEquals(3, buf1.position())
    assertEquals(4, buf2.position())

    buf2.reset()
    assertEquals(3, buf2.position())
    buf2.position(4)

    if (!createsReadOnly) {
      buf2.put(89)
      assertEquals(elemFromInt(89), buf1.get(4))
      assertEquals(5, buf2.position())
      assertEquals(3, buf1.position())
    }

    buf2.limit(5)
    assertEquals(7, buf1.limit())

    if (!createsReadOnly) {
      buf1.put(6, 23)
      buf2.limit(10)
      assertEquals(elemFromInt(23), buf2.get(6))
    }
  }
}
