/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package deeplearning.sequenceencoder

import com.kotlinnlp.simplednn.core.layers.feedforward.FeedforwardLayerParameters
import com.kotlinnlp.simplednn.deeplearning.sequenceencoder.SequenceFeedforwardEncoder
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArrayFactory
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import kotlin.test.assertTrue

/**
 *
 */
class SequenceEncoderSpec : Spek({

  describe("a SequenceFeedforwardEncoder") {

    val inputSequence = SequenceEncoderUtils.buildInputSequence()
    val network = SequenceEncoderUtils.buildNetwork()
    val encoder = SequenceFeedforwardEncoder<DenseNDArray>(network)

    val encodedSequence = encoder.encode(inputSequence)

    it("should match the expected first output array") {
      assertTrue {
        encodedSequence[0].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(0.66959, -0.793199)),
          tolerance = 1.0e-06
        )
      }
    }

    it("should match the expected second output array") {
      assertTrue {
        encodedSequence[1].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(-0.739783, 0.197375)),
          tolerance = 1.0e-06
        )
      }
    }

    it("should match the expected third output array") {
      assertTrue {
        encodedSequence[2].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(0.318521, -0.591519)),
          tolerance = 1.0e-06
        )
      }
    }

    encoder.backward(
      outputErrorsSequence = SequenceEncoderUtils.buildOutputErrorsSequence(),
      propagateToInput = true)

    val paramsErrors = encoder.getParamsErrors().paramsPerLayer[0] as FeedforwardLayerParameters

    it("should match the expected errors of the biases") {
      assertTrue {
        paramsErrors.unit.biases.values.equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(-0.096723, -0.219754)),
          tolerance = 1.0e-06
        )
      }
    }

    it("should match the expected errors of the weights") {
      assertTrue {
        (paramsErrors.unit.weights.values as DenseNDArray).equals(
          DenseNDArrayFactory.arrayOf(arrayOf(
            doubleArrayOf(-0.086611, 0.097745, -0.094472),
            doubleArrayOf(-0.165914, -0.065926, 0.136797)
          )),
          tolerance = 1.0e-06
        )
      }
    }

    val inputErrors: Array<DenseNDArray> = encoder.getInputSequenceErrors()

    it("should match the expected errors of first input array") {
      assertTrue {
        inputErrors[0].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(-0.329642, 0.160346, -0.415821)),
          tolerance = 1.0e-06
        )
      }
    }

    it("should match the expected errors of second input array") {
      assertTrue {
        inputErrors[1].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(0.221833, -0.095071, 0.316905)),
          tolerance = 1.0e-06
        )
      }
    }

    it("should match the expected errors of third input array") {
      assertTrue {
        inputErrors[2].equals(
          DenseNDArrayFactory.arrayOf(doubleArrayOf(-0.216483, 0.243231, 0.12538)),
          tolerance = 1.0e-06
        )
      }
    }
  }
})
