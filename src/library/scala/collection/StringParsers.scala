package scala
package collection

import scala.annotation.tailrec

/** A module containing the implementations of parsers from strings to numeric types, and boolean
 */
final private[scala] object StringParsers {

  //compile-time constant helpers

  //Int.MinValue == -2147483648
  private final val intOverflowBoundary = -214748364
  private final val intOverflowDigit = 9
  //Long.MinValue == -9223372036854775808L
  private final val longOverflowBoundary = -922337203685477580L
  private final val longOverflowDigit = 9

  
  @inline
  private[this] final def decValue(ch: Char): Int = java.lang.Character.digit(ch, 10)

  @inline
  private[this] final def stepToOverflow(from: String, len: Int, agg: Int, isPositive: Boolean, min: Int): Option[Int] = {
    @tailrec
    def rec(i: Int, agg: Int): Option[Int] = 
      if (agg < min) None
      else if (i == len) {
        if (!isPositive) Some(agg)
        else if (agg == min) None
        else Some(-agg)
      }
      else {
        val digit = decValue(from.charAt(i))
        if (digit == -1) None
        else rec(i + 1, agg * 10 - digit)
      }
    rec(1, agg)
  }

  //bool
  @inline
  final def parseBool(from: String): Option[Boolean] =
    if (from.equalsIgnoreCase("true")) Some(true)
    else if (from.equalsIgnoreCase("false")) Some(false)
    else None

  //integral types
  final def parseByte(from: String): Option[Byte] = {
    val len = from.length()
    //empty strings parse to None
    if (len == 0) None
    else {
      val first = from.charAt(0)
      val v = decValue(first)
      if (len == 1) {
        //"+" and "-" parse to None
        if (v > -1) Some(v.toByte)
        else None
      }
      else if (v > -1) stepToOverflow(from, len, -v, true, Byte.MinValue).map(_.toByte)
      else if (first == '+') stepToOverflow(from, len, 0, true, Byte.MinValue).map(_.toByte)
      else if (first == '-') stepToOverflow(from, len, 0, false, Byte.MinValue).map(_.toByte)
      else None
    }
  }

  final def parseShort(from: String): Option[Short] = {
    val len = from.length()
    //empty strings parse to None
    if (len == 0) None
    else {
      val first = from.charAt(0)
      val v = decValue(first)
      if (len == 1) {
        //"+" and "-" parse to None
        if (v > -1) Some(v.toShort)
        else None
      }
      else if (v > -1) stepToOverflow(from, len, -v, true, Short.MinValue).map(_.toShort)
      else if (first == '+') stepToOverflow(from, len, 0, true, Short.MinValue).map(_.toShort)
      else if (first == '-') stepToOverflow(from, len, 0, false, Short.MinValue).map(_.toShort)
      else None
    }
  }

  final def parseInt(from: String): Option[Int] = {
    val len = from.length()

    @tailrec
    def step(i: Int, agg: Int, isPositive: Boolean): Option[Int] = {
      if (i == len) {
        if (!isPositive) Some(agg)
        else if (agg == Int.MinValue) None
        else Some(-agg)
      }
      else if (agg < intOverflowBoundary) None
      else {
        val digit = decValue(from.charAt(i))
        if (digit == -1 || (agg == intOverflowBoundary && digit == intOverflowDigit)) None
        else step(i + 1, (agg * 10) - digit, isPositive)
      }
    }
    //empty strings parse to None
    if (len == 0) None
    else {
      val first = from.charAt(0)
      val v = decValue(first)
      if (len == 1) {
        //"+" and "-" parse to None
        if (v > -1) Some(v)
        else None
      }
      else if (v > -1) step(1, -v, true)
      else if (first == '+') step(1, 0, true)
      else if (first == '-') step(1, 0, false)
      else None
    }
  }
    
  final def parseLong(from: String): Option[Long] = {
    //like parseInt, but Longer
    val len = from.length()
  
    @tailrec
    def step(i: Int, agg: Long, isPositive: Boolean): Option[Long] = {
      if (i == len) {
        if (isPositive && agg == Long.MinValue) None
        else if (isPositive) Some(-agg)
        else Some(agg)
      }
      else if (agg < longOverflowBoundary) None
      else {
        val digit = decValue(from.charAt(i))
        if (digit == -1 || (agg == longOverflowBoundary && digit == longOverflowDigit)) None
        else step(i + 1, agg * 10 - digit, isPositive)
      }
    }
    //empty strings parse to None
    if (len == 0) None
    else {
      val first = from.charAt(0)
      val v = decValue(first).toLong
      if (len == 1) {
        //"+" and "-" parse to None
        if (v > -1) Some(v)
        else None
      }
      else if (v > -1) step(1, -v, true)
      else if (first == '+') step(1, 0, true)
      else if (first == '-') step(1, 0, false)
      else None
    }
  }
  
  //floating point
  final def checkFloatFormat(format: String): Boolean = {
    //indices are tracked with a start index which points *at* the first index
    //and an end index which points *after* the last index
    //so that slice length === end - start
    //thus start == end <=> empty slice
    //and format.substring(start, end) is equivalent to the slice

    //some utilities for working with index bounds into the original string
    @inline
    def forAllBetween(start: Int, end: Int, pred: Char => Boolean): Boolean = {
      def rec(i: Int): Boolean = {
        if (i >= end) true
        else if (pred(format.charAt(i))) rec(i + 1)
        else false
      }
      rec(start)
    }

    //one after last index for the predicate to hold, or `from` if none hold
    //may point after the end of the string
    @inline
    def skipIndexWhile(predicate: Char => Boolean, from: Int, until: Int): Int = {
      @tailrec @inline
      def rec(i: Int): Int = if ((i < until) && predicate(format.charAt(i))) rec(i + 1)
                             else i
      rec(from)
    }
    

    def isHexFloatLiteral(startIndex: Int, endIndex: Int): Boolean = {
      def isHexDigit(ch: Char) = ((ch >= '0' && ch <= '9') ||
                                  (ch >= 'a' && ch <= 'f') ||
                                  (ch >= 'A' && ch <= 'F'))

      def prefixOK(startIndex: Int, endIndex: Int): Boolean = {
        val len = endIndex - startIndex
        if (len == 0) false
        else {
          //the prefix part is
          //hexDigits 
          //hexDigits.
          //hexDigits.hexDigits 
          //.hexDigits
          //but notnot .
          if (format.charAt(startIndex) == '.') {
            (len > 1) && forAllBetween(startIndex + 1, endIndex, isHexDigit)
          } else {
            val noLeading = skipIndexWhile(isHexDigit, startIndex, endIndex)
            if (noLeading >= endIndex) true
            else if (format.charAt(noLeading) == '.') forAllBetween(noLeading + 1, endIndex, isHexDigit)
            else false
          }
        }
      }

      def postfixOK(startIndex: Int, endIndex: Int): Boolean = {
        if (startIndex >= endIndex) false
        else {
          if (forAllBetween(startIndex, endIndex, ch => ch >= '0' && ch <= '9')) true
          else {
            val startchar = format.charAt(startIndex)
            (startchar == '+' || startchar == '-') && (endIndex - startIndex > 1) && forAllBetween(startIndex + 1, endIndex, ch => ch >= '0' && ch <= '9')
          }
        }

      }
      // prefix [pP] postfix
      val pIndex = format.indexWhere(ch => ch == 'p' || ch == 'P', startIndex)
      (pIndex <= endIndex) && prefixOK(startIndex, pIndex) && postfixOK(pIndex + 1, endIndex)
    }
 
    def isDecFloatLiteral(startIndex: Int, endIndex: Int): Boolean = {
      //invariant: endIndex > startIndex

      def expOK(startIndex: Int, endIndex: Int): Boolean = {
        if (startIndex >= endIndex) false
        else {
          val startChar = format.charAt(startIndex)
          if (startChar == '+' || startChar == '-')
            (endIndex > (startIndex + 1)) &&
            skipIndexWhile(ch => ch >= '0' && ch <= '9', startIndex + 1, endIndex) == endIndex
          else skipIndexWhile(ch => ch >= '0' && ch <= '9', startIndex, endIndex) == endIndex
        }
      }

      //significant can be one of
      //* digits.digits
      //* .digits
      //* digits.
      //but not just .
      val startChar = format.charAt(startIndex)
      if (startChar == '.') {
        val noSignificant = skipIndexWhile(ch => ch >= '0' && ch <= '9', startIndex + 1, endIndex)
        if (noSignificant == startIndex + 1) false //not just "." or ".Exxx"
        else {
          val e = format.charAt(noSignificant)
          if (e == 'e' || e == 'E') expOK(noSignificant + 1, endIndex)
          else false
        }
      }
      else if (startChar >= '0' && startChar <= '9'){
         //one set of digits, then optionally a period, then optionally another set of digits, then optionally an exponent
        val noInt = skipIndexWhile(ch => ch >= '0' && ch <= '9', startIndex, endIndex)
        if (noInt == endIndex) true //just the digits
        else {
          val afterIntChar = format.charAt(noInt)
          if (afterIntChar == '.') {
            val noSignificant = skipIndexWhile(ch => ch >= '0' && ch <= '9', noInt + 1, endIndex)
            if (noSignificant >= endIndex) true //no exponent
            else {
              val e = format.charAt(noSignificant)
              (e == 'e' || e == 'E') && expOK(noSignificant + 1, endIndex)
            }
          }
          else if (afterIntChar == 'e' || afterIntChar == 'E') expOK(noInt + 1, endIndex)
          else false
        }
      }
      else false
 
    }

    //count 0x00 to 0x20 as "whitespace", and nothing else
    val unspacedStart = format.indexWhere(ch => ch.toInt > 0x20)
    val unspacedEnd = format.lastIndexWhere(ch => ch.toInt > 0x20) + 1
    
    if (unspacedStart == -1 || unspacedStart >= unspacedEnd || unspacedEnd <= 0) false
    else {
      //all formats can have a sign
      val unsigned = {
        val startchar = format.charAt(unspacedStart)
        if (startchar == '-' || startchar == '+') unspacedStart + 1 else unspacedStart
      }
      if (unsigned >= unspacedEnd) false
      //that's it for NaN and Infinity
      else if (format.charAt(unsigned) == 'N') format.substring(unsigned, unspacedEnd) == "NaN"
      else if (format.charAt(unsigned) == 'I') format.substring(unsigned, unspacedEnd) == "Infinity"
      else {
        //all other formats can have a format suffix
        val desuffixed = {
          val endchar = format.charAt(unspacedEnd - 1)
          if (endchar == 'f' || endchar == 'F' || endchar == 'd' || endchar == 'D') unspacedEnd - 1
          else unspacedEnd
        }
        val len = desuffixed - unsigned
        if (len <= 0) false
        else if (len >= 2 && (format.charAt(unsigned + 1) == 'x' || format.charAt(unsigned + 1) == 'X'))
          format.charAt(unsigned) == '0' && isHexFloatLiteral(unsigned + 2, desuffixed)
        else isDecFloatLiteral(unsigned, desuffixed)
      }
    }
  }
    
  @inline
  def parseFloat(from: String): Option[Float] =
    if (checkFloatFormat(from)) Some(java.lang.Float.parseFloat(from))
    else None

  @inline
  def parseDouble(from: String): Option[Double] =
    if (checkFloatFormat(from)) Some(java.lang.Double.parseDouble(from))
    else None

}