package com.example.libraryapp.calculator

fun main() {
    val calculatorTest = CalculatorTest()
    calculatorTest.addTest2()
    calculatorTest.minusTest()
    calculatorTest.multiplyTest()
}

internal class CalculatorTest {

    // data class 를 이용
    fun addTest() {
        // given
        val calculator = Calculator(5)

        // when
        calculator.add(3)

        // then
        val expectedCalculator = Calculator(8)
        if (calculator != expectedCalculator) {
            throw IllegalStateException()
        }
    }

    fun addTest2() {
        val calculator = Calculator(5)
        calculator.add(3)

        if (calculator.number != 8) {
            throw IllegalStateException()
        }
    }

    fun minusTest() {
        // given
        val calculator = Calculator(5)

        // when
        calculator.minus(3)

        // then
        if (calculator.number != 2) {
            throw IllegalStateException()
        }
    }

    fun multiplyTest() {
        // given
        val calculator = Calculator(5)

        // when
        calculator.multiply(3)

        // then
        if (calculator.number != 15) {
            throw IllegalStateException()
        }
    }
}