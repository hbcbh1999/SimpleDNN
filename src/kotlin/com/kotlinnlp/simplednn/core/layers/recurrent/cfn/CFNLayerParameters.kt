/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.simplednn.core.layers.recurrent.cfn

import com.kotlinnlp.simplednn.core.layers.recurrent.GateParametersUnit
import com.kotlinnlp.simplednn.core.layers.LayerParameters
import com.kotlinnlp.simplednn.core.arrays.UpdatableArray
import com.kotlinnlp.simplednn.core.functionalities.randomgenerators.RandomGenerator
import com.kotlinnlp.simplednn.simplemath.ndarray.Shape

/**
 *
 * @param inputSize input size
 * @param outputSize output size
 */
class CFNLayerParameters(inputSize: Int, outputSize: Int) : LayerParameters(inputSize, outputSize) {

  /**
   *
   */
  val inputGate = GateParametersUnit(inputSize, outputSize)

  /**
   *
   */
  val forgetGate = GateParametersUnit(inputSize, outputSize)

  /**
   *
   */
  val candidateWeights = UpdatableArray(Shape(outputSize, inputSize))

  /**
   *
   */
  init {

    this.paramsList = arrayListOf(
      this.inputGate.weights,
      this.forgetGate.weights,
      this.candidateWeights,

      this.inputGate.biases,
      this.forgetGate.biases,

      this.inputGate.recurrentWeights,
      this.forgetGate.recurrentWeights
    )
  }

  /**
   *
   */
  override fun initialize(randomGenerator: RandomGenerator, biasesInitValue: Double) {

    this.inputGate.weights.values.randomize(randomGenerator)
    this.forgetGate.weights.values.randomize(randomGenerator)
    this.candidateWeights.values.randomize(randomGenerator)

    this.inputGate.biases.values.assignValues(biasesInitValue)
    this.forgetGate.biases.values.assignValues(biasesInitValue)

    this.inputGate.recurrentWeights.values.randomize(randomGenerator)
    this.forgetGate.recurrentWeights.values.randomize(randomGenerator)
  }
}