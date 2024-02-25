/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import kotlin.random.Random

/** A [Sequence] that yields the contents of [values] in an infinite loop. */
class InfiniteSequence<T>(val values: List<T>): Sequence<T> {

    override fun iterator() = object: Iterator<T> {
        private var currentIndex: Int = -1

        override fun hasNext() = true

        override fun next(): T {
            if (currentIndex == values.lastIndex)
                currentIndex = 0
            else ++currentIndex

            return values[currentIndex]
        }
    }
}

/**
 * An infinite [Sequence] that yields the values of [unshuffledValues] in
 * random order, with optional memory to prevent close repetition of values.
 *
 * ShuffledInfiniteSequence's [iterator] is infinite, and will yield each value
 * of [unshuffledValues] once in completely random order before repeating,
 * after which all of the values will be yielded in a new random order.
 *
 * When [memorySize] is greater than zero, the last values on a given loop
 * will not be chosen from when yielding the first [memorySize] values on the
 * next loop. With a [memorySize] value of three for example, the last three
 * values yielded on a given loop will not be chosen from when selecting the
 * first of the next loop, the last two values will not be chosen from when
 * selecting the second of the next loop, and the last value will not be
 * chosen when selecting the third of the next loop. This will prevent the
 * possibility of yielding the same values twice in close proximity at the
 * expense of the randomness of the order. At the maximum [memorySize] value
 * of one less than the size of [unshuffledValues], the random order of the
 * first loop of the values will be repeated infinitely. [memorySize] values
 * outside the range of [0, [unshuffledValues].size - 1] will be coerced into
 * this range.
 */
class ShuffledInfiniteSequence<T>(
    val unshuffledValues: List<T>,
    memorySize: Int = 0,
): Sequence<T> {
    val memorySize = memorySize.coerceIn(0, unshuffledValues.size - 1)

    override fun iterator() = object: Iterator<T> {
        private val values = unshuffledValues.toMutableList()
        private var currentIndex: Int = -1
        private var firstIteration = true

        override fun hasNext() = true
        override fun next(): T {
            if (currentIndex == values.lastIndex) {
                currentIndex = 0
                firstIteration = false
            } else ++currentIndex

            val until = if (firstIteration) values.size
                        else values.size - maxOf(memorySize - currentIndex, 0)
            val randomIndex = Random.nextInt(currentIndex, until)
            if (randomIndex != currentIndex) {
                val randomValue = values[randomIndex]
                values[randomIndex] = values[currentIndex]
                values[currentIndex] = randomValue
            }
            return values[currentIndex]
        }
    }
}