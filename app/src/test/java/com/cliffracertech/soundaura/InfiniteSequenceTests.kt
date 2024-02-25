/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import com.cliffracertech.soundaura.service.InfiniteSequence
import com.cliffracertech.soundaura.service.ShuffledInfiniteSequence
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InfiniteSequenceTests {
    private val values = List(20) { it }

    /** Create a [List] from the first [size] values yielded by the receiver [Iterator]. */
    private fun <T> Iterator<T>.toList(size: Int) = List(size) { next() }

    private fun <T> List<T>.lastNValues(n: Int) =
        subList(size - n.coerceAtMost(size), size)

    @Test fun unshuffled_sequence_infinitely_repeats() {
        val iterator = InfiniteSequence(values).iterator()
        val result = iterator.toList(50)
        val expected = values + values + values.subList(0, 10)
        assertThat(result).containsExactlyElementsIn(expected).inOrder()
    }

    @Test fun shuffled_sequence_with_no_memory_has_new_order_each_iteration() {
        val iterator = ShuffledInfiniteSequence(values).iterator()
        val firstIt = iterator.toList(values.size)
        assertThat(firstIt).containsExactlyElementsIn(values)
        assertThat(firstIt == values).isFalse()

        val secondIt = iterator.toList(values.size)
        assertThat(secondIt).containsExactlyElementsIn(values)
        assertThat(secondIt).isNotEqualTo(values)
        assertThat(secondIt).isNotEqualTo(firstIt)

        val thirdIt = List(values.size) { iterator.next() }
        assertThat(thirdIt).containsExactlyElementsIn(values)
        assertThat(thirdIt).isNotEqualTo(values)
        assertThat(thirdIt).isNotEqualTo(firstIt)
        assertThat(thirdIt).isNotEqualTo(secondIt)
    }

    @Test fun shuffled_sequence_with_partial_memory_has_no_close_repeats() {
        val memorySize = values.size / 2
        val iterator = ShuffledInfiniteSequence(values, memorySize).iterator()
        val iterations = List(5) { iterator.toList(values.size) }

        iterations.forEachIndexed { index, iteration ->
            assertThat(iteration).containsExactlyElementsIn(values)
            val lastIteration = iterations.getOrElse(index - 1) { emptyList() }
            for (i in 0..memorySize) {
                val excluded = lastIteration.lastNValues(memorySize - i)
                assertThat(iteration[i]).isNotIn(excluded)
            }
        }
        assertThat(iterations).containsNoDuplicates()
    }

    @Test fun shuffled_sequence_with_max_memory_has_one_random_order_that_infinitely_repeats() {
        val memorySize = values.size - 1
        val iterator = ShuffledInfiniteSequence(values, memorySize).iterator()
        val iterations = List(5) { iterator.toList(values.size) }
        val firstIteration = iterations.first()

        assertThat(firstIteration).containsExactlyElementsIn(values)
        assertThat(firstIteration).isNotEqualTo(values)
        val expected = List(iterations.size) { firstIteration }
        assertThat(iterations).containsExactlyElementsIn(expected)
    }
}
